package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;
import org.femass.dto.*;
import org.femass.entity.*;
import org.femass.exception.AdminException;
import org.femass.repository.*;
import java.util.*;

@ApplicationScoped
public class AdministracaoCampanhaService {
    @Inject CampanhaRepository campanhaRepository;
    @Inject FormularioRepository formularioRepository;
    @Inject FormularioVersaoRepository versaoRepository;
    @Inject PerguntaVersaoRepository perguntaRepository;
    @Inject OpcaoPerguntaRepository opcaoRepository;

    public List<AdminViews.Campanha> listar() {
        return campanhaRepository.find("order by nome, id").list().stream().map(this::view).toList();
    }
    public AdminViews.Campanha consultar(String codigo) { return view(campanha(codigo)); }
    public AdminViews.Campanha view(Campanha c) {
        return new AdminViews.Campanha(c.getId(), c.getCodigo(), c.getNome(), c.getMensagemDisponibilidade(), c.getEstado().name(), c.isAtiva());
    }
    @Transactional
    public Campanha criarCampanha(String codigo, String nome, String mensagem) {
        codigo = texto(codigo, "Código", 50);
        if (campanhaRepository.findByCodigo(codigo) != null) throw conflict("Código de campanha já existe");
        Campanha c = new Campanha(); c.setCodigo(codigo); preencherCampanha(c, nome, mensagem);
        campanhaRepository.persistAndFlush(c); return c;
    }
    @Transactional
    public void atualizarCampanha(String codigo, AdminCampanhaDTO dto) {
        if (dto == null) throw bad("Dados da campanha são obrigatórios");
        if (dto.codigo != null && !codigo.equals(dto.codigo)) throw conflict("Código da campanha não pode ser alterado");
        preencherCampanha(campanhaParaEdicao(codigo), dto.nome, dto.mensagem);
    }
    private void preencherCampanha(Campanha c, String nome, String mensagem) {
        c.setNome(texto(nome, "Nome", 150)); c.setMensagemDisponibilidade(opcional(mensagem, "Mensagem", 500));
    }
    @Transactional public void statusCampanha(String codigo, AdminViews.Status dto) { campanhaParaEdicao(codigo).setAtiva(ativo(dto)); }
    public List<AdminViews.Formulario> formularios(String codigo) {
        Campanha c = campanha(codigo);
        return formularioRepository.find("campanha.id = ?1 order by codigo", c.getId()).list().stream().map(this::view).toList();
    }
    public AdminViews.Formulario consultarFormulario(String campanha, String codigo) { return view(formulario(campanha, codigo)); }
    private AdminViews.Formulario view(Formulario f) {
        return new AdminViews.Formulario(f.getId(), f.getCodigo(), f.getNome(), f.getPublico().getValor(), f.isAtivo());
    }
    public List<AdminViews.Versao> versoes(String campanha, String codigo) {
        Formulario f = formulario(campanha, codigo);
        return versaoRepository.find("formulario.id = ?1 order by numero desc", f.getId()).list().stream().map(this::view).toList();
    }
    public AdminViews.Versao consultarVersao(String c, String f, Integer numero) { return view(versao(c, f, numero)); }
    private AdminViews.Versao view(FormularioVersao v) {
        return new AdminViews.Versao(v.getId(), v.getNumero(), v.getFormulario().getCodigo(), v.getNome(), v.getPublico().getValor(),
            v.getEscopo().name(), v.getOrdem(), v.isComentarioPermitido(), v.getAvisoComentario(), v.getEstado().name(), v.isAtivo(),
            perguntaRepository.findByVersion(v.getId()).stream().map(p -> new AdminViews.Pergunta(p.getId(), p.getCodigo(), p.getTexto(), p.getOrdem(), p.isAtivo(),
                opcaoRepository.findByQuestion(p.getId()).stream().map(o -> new AdminViews.Opcao(o.getId(), o.getCodigo(), o.getRotulo(), o.getValor(), o.isNaoSeiResponder(), o.isAtivo())).toList())).toList());
    }
    @Transactional public void statusFormulario(String c, String f, AdminViews.Status dto) { formularioParaEdicao(c, f).setAtivo(ativo(dto)); }
    @Transactional public void statusVersao(String c, String f, Integer n, AdminViews.Status dto) { versaoParaEdicao(c, f, n).setAtivo(ativo(dto)); }

    @Transactional
    public FormularioVersao criarFormulario(String codigo, AdminFormularioDTO dto) {
        validar(dto);
        Campanha c = campanhaParaEdicao(codigo);
        String formCode = texto(dto.codigo, "Código do formulário", 60);
        if (formularioRepository.findByCampaignAndCode(codigo, formCode) != null) throw conflict("Código de formulário já existe na campanha");
        Formulario f = new Formulario(); f.setCodigo(formCode); f.setNome(dto.nome.trim()); f.setPublico(publico(dto.publico)); f.setCampanha(c);
        formularioRepository.persist(f);
        FormularioVersao v = new FormularioVersao(); v.setFormulario(f); v.setNumero(1); preencher(v, dto); versaoRepository.persist(v);
        sincronizar(v, dto.perguntas); c.setEstado(EstadoCampanha.RASCUNHO); versaoRepository.flush(); return v;
    }
    @Transactional
    public FormularioVersao clonarVersao(String c, String f) {
        Formulario formulario = formularioParaEdicao(c, f);
        FormularioVersao origem = versaoRepository.findLatest(formulario.getId());
        if (origem == null) throw conflict("Formulário não possui versão");
        FormularioVersao destino = new FormularioVersao();
        destino.setFormulario(formulario); destino.setNumero(origem.getNumero() + 1); destino.setNome(origem.getNome());
        destino.setPublico(origem.getPublico()); destino.setOrdem(origem.getOrdem()); destino.setEscopo(origem.getEscopo());
        destino.setComentarioPermitido(origem.isComentarioPermitido()); destino.setAvisoComentario(origem.getAvisoComentario());
        versaoRepository.persist(destino);
        for (PerguntaVersao p : perguntaRepository.findByVersion(origem.getId())) {
            PerguntaVersao copia = new PerguntaVersao(); copia.setFormularioVersao(destino); copia.setCodigo(p.getCodigo());
            copia.setTexto(p.getTexto()); copia.setOrdem(p.getOrdem()); copia.setAtivo(p.isAtivo()); perguntaRepository.persist(copia);
            for (OpcaoPergunta o : opcaoRepository.findByQuestion(p.getId())) {
                OpcaoPergunta nova = new OpcaoPergunta(); nova.setPergunta(copia); nova.setCodigo(o.getCodigo()); nova.setRotulo(o.getRotulo());
                nova.setValor(o.getValor()); nova.setNaoSeiResponder(o.isNaoSeiResponder()); nova.setOrdem(o.getOrdem()); nova.setAtivo(o.isAtivo()); opcaoRepository.persist(nova);
            }
        }
        formulario.getCampanha().setEstado(EstadoCampanha.RASCUNHO); versaoRepository.flush(); return destino;
    }
    @Transactional
    public void atualizarVersao(String c, String f, Integer numero, AdminFormularioDTO dto) {
        FormularioVersao v = versaoParaEdicao(c, f, numero);
        if (v.getEstado() != EstadoFormularioVersao.RASCUNHO) throw conflict("Somente versões em rascunho podem ser alteradas");
        validar(dto);
        if (dto.codigo != null && !f.equals(dto.codigo)) throw conflict("Código do formulário não pode ser alterado");
        preencher(v, dto); sincronizar(v, dto.perguntas); v.getFormulario().getCampanha().setEstado(EstadoCampanha.RASCUNHO);
    }
    private void preencher(FormularioVersao v, AdminFormularioDTO dto) {
        v.setNome(dto.nome.trim()); v.setPublico(publico(dto.publico)); v.setOrdem(dto.ordem == null ? 1 : dto.ordem);
        v.setEscopo(escopo(dto.escopo)); v.setComentarioPermitido(dto.commentAllowed);
        v.setAvisoComentario(dto.commentAllowed ? dto.commentNotice.trim() : null);
    }
    private void sincronizar(FormularioVersao v, List<AdminPerguntaDTO> perguntas) {
        List<PerguntaVersao> existentes = perguntaRepository.findByVersion(v.getId());
        existentes.forEach(p -> p.setAtivo(false));
        for (AdminPerguntaDTO dto : perguntas) {
            String code = dto.codigo.trim();
            PerguntaVersao p = existentes.stream().filter(x -> x.getCodigo().equals(code)).findFirst().orElse(null);
            if (p == null) { p = new PerguntaVersao(); p.setFormularioVersao(v); p.setCodigo(code); }
            p.setTexto(dto.texto.trim()); p.setOrdem(dto.ordem == null ? 1 : dto.ordem); p.setAtivo(dto.ativo);
            if (p.getId() == null) perguntaRepository.persist(p);
            List<OpcaoPergunta> opcoes = opcaoRepository.findByQuestion(p.getId()); opcoes.forEach(o -> o.setAtivo(false));
            for (AdminOpcaoDTO input : dto.opcoes) {
                String optionCode = input.code.trim();
                OpcaoPergunta o = opcoes.stream().filter(x -> x.getCodigo().equals(optionCode)).findFirst().orElse(null);
                if (o == null) { o = new OpcaoPergunta(); o.setPergunta(p); o.setCodigo(optionCode); }
                o.setRotulo(input.label.trim()); o.setValor(input.value); o.setNaoSeiResponder(input.naoSeiResponder);
                o.setOrdem(input.value == null ? 3 : input.value); o.setAtivo(input.ativo);
                if (o.getId() == null) opcaoRepository.persist(o);
            }
        }
    }
    @Transactional
    public void publicarVersao(String c, String f, Integer n) {
        FormularioVersao v = versaoParaEdicao(c, f, n);
        if (v.getEstado() != EstadoFormularioVersao.RASCUNHO) throw conflict("Versão em rascunho não encontrada");
        if (!v.isAtivo() || !v.getFormulario().isAtivo() || !v.getFormulario().getCampanha().isAtiva()) throw conflict("Reative a campanha, formulário e versão antes de publicar");
        List<PerguntaVersao> perguntas = perguntaRepository.findByVersion(v.getId()).stream().filter(PerguntaVersao::isAtivo).toList();
        if (perguntas.isEmpty()) throw bad("Formulário deve ter perguntas ativas");
        for (PerguntaVersao p : perguntas) {
            if (opcaoRepository.findByQuestion(p.getId()).stream().noneMatch(OpcaoPergunta::isAtivo)) throw bad("Cada pergunta ativa deve ter opções ativas");
        }
        v.setEstado(EstadoFormularioVersao.PUBLICADA); v.getFormulario().getCampanha().setEstado(EstadoCampanha.RASCUNHO);
    }
    @Transactional public void aprovar(String codigo) {
        Campanha c = campanhaParaEdicao(codigo);
        if (!c.isAtiva() || c.getEstado() != EstadoCampanha.RASCUNHO) throw conflict("Campanha ativa em rascunho é necessária para aprovação");
        if (versaoRepository.count("formulario.campanha.id = ?1 and formulario.ativo = true and ativo = true and estado = 'PUBLICADA'", c.getId()) == 0)
            throw conflict("Campanha deve possuir ao menos uma versão publicada antes da aprovação");
        c.setEstado(EstadoCampanha.APROVADA);
    }
    @Transactional public void abrir(String codigo) {
        Campanha c = campanhaParaEdicao(codigo);
        if (!c.isAtiva() || c.getEstado() != EstadoCampanha.APROVADA) throw conflict("Campanha ativa deve estar aprovada antes de abrir");
        c.setEstado(EstadoCampanha.ABERTA);
    }
    @Transactional public void encerrar(String codigo) { campanhaParaEdicao(codigo).setEstado(EstadoCampanha.ENCERRADA); }

    // Serialize content/publication/status changes to preserve published versions under concurrent requests.
    private Campanha campanhaParaEdicao(String codigo) {
        Campanha c = campanhaRepository.find("codigo", codigo).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (c == null) throw missing("Campanha não encontrada"); return c;
    }
    private Formulario formularioParaEdicao(String c, String codigo) {
        Campanha campaign = campanhaParaEdicao(c);
        Formulario f = formularioRepository.find("campanha.id = ?1 and codigo = ?2", campaign.getId(), codigo)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (f == null) throw missing("Formulário não encontrado"); return f;
    }
    private FormularioVersao versaoParaEdicao(String c, String f, Integer numero) {
        Formulario form = formularioParaEdicao(c, f);
        FormularioVersao v = versaoRepository.find("formulario.id = ?1 and numero = ?2", form.getId(), numero)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (v == null) throw missing("Versão não encontrada"); return v;
    }

    private Campanha campanha(String codigo) {
        Campanha c = campanhaRepository.findByCodigo(codigo); if (c == null) throw missing("Campanha não encontrada"); return c;
    }
    private Formulario formulario(String c, String codigo) {
        campanha(c); Formulario f = formularioRepository.findByCampaignAndCode(c, codigo); if (f == null) throw missing("Formulário não encontrado"); return f;
    }
    private FormularioVersao versao(String c, String f, Integer numero) {
        FormularioVersao v = versaoRepository.findByFormularioAndNumero(formulario(c, f).getId(), numero);
        if (v == null) throw missing("Versão não encontrada"); return v;
    }
    private static void validar(AdminFormularioDTO dto) {
        if (dto == null) throw bad("Dados do formulário são obrigatórios");
        texto(dto.nome, "Nome", 180); publico(dto.publico); escopo(dto.escopo); ordem(dto.ordem);
        if (dto.commentAllowed) texto(dto.commentNotice, "Aviso de comentário", 500);
        if (dto.perguntas == null || dto.perguntas.isEmpty()) throw bad("Formulário deve ter perguntas");
        Set<String> codes = new HashSet<>();
        for (AdminPerguntaDTO p : dto.perguntas) {
            if (p == null) throw bad("Pergunta inválida");
            if (!codes.add(texto(p.codigo, "Código da pergunta", 40))) throw conflict("Código de pergunta duplicado");
            texto(p.texto, "Texto da pergunta", 1000); ordem(p.ordem);
            if (p.opcoes == null || p.opcoes.isEmpty()) throw bad("Pergunta deve ter opções");
            Set<String> optionCodes = new HashSet<>();
            for (AdminOpcaoDTO o : p.opcoes) {
                if (o == null) throw bad("Opção inválida");
                if (!optionCodes.add(texto(o.code, "Código da opção", 30))) throw conflict("Código de opção duplicado");
                texto(o.label, "Rótulo da opção", 100);
                if ((!o.naoSeiResponder && (o.value == null || o.value < 1 || o.value > 5)) || (o.naoSeiResponder && o.value != null))
                    throw bad("Valor deve ser de 1 a 5, ou nulo para não sei responder");
            }
        }
    }
    private static void ordem(Integer ordem) { if (ordem != null && ordem < 1) throw bad("Ordem deve ser positiva"); }
    private static PublicoAvaliacao publico(String p) {
        texto(p, "Público", 20); try { return PublicoAvaliacao.from(p); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
    }
    private static EscopoFormulario escopo(String value) {
        try { return EscopoFormulario.valueOf(texto(value, "Escopo", 20).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw bad("Escopo deve ser DISCIPLINA ou GERAL"); }
    }
    public static String texto(String value, String campo, int max) {
        if (value == null || value.isBlank()) throw bad(campo + " é obrigatório");
        if (value.trim().length() > max) throw bad(campo + " deve ter até " + max + " caracteres"); return value.trim();
    }
    private static String opcional(String value, String campo, int max) {
        if (value == null || value.isBlank()) return null; return texto(value, campo, max);
    }
    public static boolean ativo(AdminViews.Status dto) {
        if (dto == null || dto.ativo() == null) throw bad("Estado ativo é obrigatório"); return dto.ativo();
    }
    public static AdminException bad(String message) { return new AdminException(400, message); }
    public static AdminException conflict(String message) { return new AdminException(409, message); }
    public static AdminException missing(String message) { return new AdminException(404, message); }
}
