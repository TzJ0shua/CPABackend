package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.femass.dto.ConfirmarVerificacaoEmailDTO;
import org.femass.dto.SolicitarVerificacaoEmailDTO;
import org.femass.entity.PublicoAvaliacao;
import org.femass.entity.StatusVerificacaoEmail;
import org.femass.entity.VerificacaoEmail;
import org.femass.repository.VerificacaoEmailRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@ApplicationScoped
public class VerificacaoEmailService {
    private static final char[] PIN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject DisponibilidadeCampanhaService disponibilidade;
    @Inject org.femass.repository.FormularioVersaoRepository versoes;
    @Inject VerificacaoEmailRepository repository;
    @Inject EmailSender emailSender;
    @ConfigProperty(name = "verificacao-email.pin-expiracao", defaultValue = "PT2H") Duration pinExpiracao;
    @ConfigProperty(name = "verificacao-email.autorizacao-expiracao", defaultValue = "PT15M") Duration autorizacaoExpiracao;
    @ConfigProperty(name = "verificacao-email.max-tentativas", defaultValue = "5") int maxTentativas;
    @ConfigProperty(name = "verificacao-email.max-reenvios-janela", defaultValue = "3") int maxReenviosJanela;
    @ConfigProperty(name = "verificacao-email.janela-reenvio", defaultValue = "PT1H") Duration janelaReenvio;
    @ConfigProperty(name = "verificacao-email.email-digest-secret") String emailDigestSecret;

    @Transactional
    public Long solicitar(SolicitarVerificacaoEmailDTO dto) {
        DadosSolicitacao dados = validarSolicitacao(dto);
        disponibilidade.exigirDisponivel(dados.campanha());
        if (versoes.findPublicada(dados.campanha(), dados.formulario(), dados.publico(), dados.versao()) == null)
            throw new IllegalArgumentException("Formulário publicado não disponível");
        String emailDigest = calcularEmailDigest(dados.email());
        LocalDateTime agora = LocalDateTime.now();
        if (repository.countSolicitacoesDesde(emailDigest, agora.minus(janelaReenvio)) >= maxReenviosJanela) {
            throw new IllegalStateException("Limite de solicitacoes excedido. Tente novamente mais tarde");
        }

        VerificacaoEmail anterior = repository.findPendenteForUpdate(emailDigest, dados.publico(), dados.campanha(), dados.formulario(), dados.versao());
        int reenvios = 0;
        if (anterior != null) {
            anterior.setStatus(StatusVerificacaoEmail.INVALIDADO);
            reenvios = anterior.getReenvios() + 1;
        }

        String pin = gerarSegredo(16);
        VerificacaoEmail verificacao = new VerificacaoEmail();
        verificacao.setEmailDigest(emailDigest);
        verificacao.setCodigoDigest(calcularDigest(pin));
        verificacao.setPublico(dados.publico());
        verificacao.setCampanha(dados.campanha());
        verificacao.setFormulario(dados.formulario());
        verificacao.setVersaoFormulario(dados.versao());
        verificacao.setStatus(StatusVerificacaoEmail.PENDENTE);
        verificacao.setDataCriacao(agora);
        verificacao.setExpiraEm(agora.plus(pinExpiracao));
        verificacao.setTentativas(0);
        verificacao.setReenvios(reenvios);
        repository.persist(verificacao);
        repository.flush();

        try {
            emailSender.enviarPin(dados.email(), pin);
        } catch (RuntimeException e) {
            verificacao.setStatus(StatusVerificacaoEmail.INVALIDADO);
            throw e;
        }
        return verificacao.getId();
    }

    @Transactional
    public String confirmar(ConfirmarVerificacaoEmailDTO dto) {
        if (dto == null || dto.verificationId == null || dto.pin == null || dto.pin.isBlank()) {
            throw new IllegalArgumentException("Dados de verificacao invalidos");
        }
        VerificacaoEmail verificacao = repository.findByIdForUpdate(dto.verificationId);
        if (verificacao == null || verificacao.getStatus() != StatusVerificacaoEmail.PENDENTE) {
            throw new IllegalArgumentException("Codigo de verificacao invalido");
        }
        LocalDateTime agora = LocalDateTime.now();
        if (!verificacao.getExpiraEm().isAfter(agora)) {
            verificacao.setStatus(StatusVerificacaoEmail.INVALIDADO);
            throw new IllegalStateException("Codigo de verificacao expirado");
        }
        if (verificacao.getTentativas() >= maxTentativas) {
            verificacao.setStatus(StatusVerificacaoEmail.INVALIDADO);
            throw new IllegalStateException("Limite de tentativas excedido");
        }

        verificacao.setTentativas(verificacao.getTentativas() + 1);
        if (!MessageDigest.isEqual(calcularDigest(dto.pin).getBytes(StandardCharsets.US_ASCII),
                verificacao.getCodigoDigest().getBytes(StandardCharsets.US_ASCII))) {
            if (verificacao.getTentativas() >= maxTentativas) verificacao.setStatus(StatusVerificacaoEmail.INVALIDADO);
            throw new IllegalArgumentException("Codigo de verificacao invalido");
        }

        String autorizacao = gerarSegredo(32);
        verificacao.setAutorizacaoDigest(calcularDigest(autorizacao));
        verificacao.setAutorizacaoExpiraEm(agora.plus(autorizacaoExpiracao));
        verificacao.setCodigoValidadoEm(agora);
        verificacao.setStatus(StatusVerificacaoEmail.VERIFICADO);
        return autorizacao;
    }

    @Transactional
    public void consumirAutorizacaoParaEnvio(String token, PublicoAvaliacao publico,
                                              String campanha, String formulario, Integer versao) {
        if (publico != PublicoAvaliacao.PROFESSOR && publico != PublicoAvaliacao.FUNCIONARIO) return;
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Verificacao de e-mail e obrigatoria para este publico");
        VerificacaoEmail verificacao = repository.findByAutorizacaoDigestForUpdate(calcularDigest(token));
        if (verificacao == null || verificacao.getStatus() != StatusVerificacaoEmail.VERIFICADO
                || !verificacao.getAutorizacaoExpiraEm().isAfter(LocalDateTime.now())
                || verificacao.getPublico() != publico
                || !verificacao.getCampanha().equals(campanha)) {
            throw new IllegalArgumentException("Verificacao de e-mail invalida ou expirada");
        }
        // A autorização é vinculada ao público e à campanha, não a um formulário
        // individual. Isso permite concluir a jornada prevista (por exemplo,
        // disciplinas, gestão e instituição) sem solicitar outro PIN. A versão e
        // o formulário continuam validados por FormularioService ao salvar.
    }

    private DadosSolicitacao validarSolicitacao(SolicitarVerificacaoEmailDTO dto) {
        if (dto == null || dto.email == null || !dto.email.matches("^[A-Za-z._%+-]+@femass\\.edu\\.br$")) {
            throw new IllegalArgumentException("E-mail institucional invalido");
        }
        PublicoAvaliacao publico = PublicoAvaliacao.from(dto.publico);
        if (publico != PublicoAvaliacao.PROFESSOR && publico != PublicoAvaliacao.FUNCIONARIO) {
            throw new IllegalArgumentException("Verificacao de e-mail disponivel apenas para professor ou funcionario");
        }
        if (isBlank(dto.campaign) || isBlank(dto.form) || dto.formVersion == null) {
            throw new IllegalArgumentException("Campanha, formulario e versao sao obrigatorios");
        }
        return new DadosSolicitacao(dto.email.trim().toLowerCase(), publico, dto.campaign, dto.form, dto.formVersion);
    }

    private String calcularEmailDigest(String email) {
        try {
            if (isBlank(emailDigestSecret) || "not-configured".equals(emailDigestSecret)
                    || "dev-only-change-me".equals(emailDigestSecret)) {
                throw new IllegalStateException("Servico de verificacao indisponivel");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(emailDigestSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(email.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel processar a verificacao");
        }
    }

    private String calcularDigest(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel processar a verificacao");
        }
    }

    private String gerarSegredo(int tamanho) {
        StringBuilder valor = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) valor.append(PIN_ALPHABET[RANDOM.nextInt(PIN_ALPHABET.length)]);
        return valor.toString();
    }

    private boolean isBlank(String valor) { return valor == null || valor.isBlank(); }
    private record DadosSolicitacao(String email, PublicoAvaliacao publico, String campanha, String formulario, Integer versao) { }
}
