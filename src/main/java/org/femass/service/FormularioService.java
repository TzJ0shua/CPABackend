package org.femass.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.femass.dto.CursoDTO;
import org.femass.dto.FormularioDTO;
import org.femass.dto.RespostaDTO;
import org.femass.dto.SubjectDTO;
import org.femass.entity.Avaliacao;
import org.femass.entity.Curso;
import org.femass.entity.Disciplina;
import org.femass.entity.PublicoAvaliacao;
import org.femass.entity.Resposta;
import org.femass.entity.EscopoFormulario;

import org.femass.entity.Validacao;
import org.femass.repository.AvaliacaoRepository;
import org.femass.repository.CursoRepository;
import org.femass.repository.DisciplinaRepository;
import org.femass.repository.FormularioVersaoRepository;
import org.femass.repository.PerguntaVersaoRepository;
import org.femass.repository.OpcaoPerguntaRepository;
import org.femass.entity.FormularioVersao;
import org.femass.entity.PerguntaVersao;
import org.femass.entity.OpcaoPergunta;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FormularioService {

    @Inject
    CursoRepository cursoRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject FormularioVersaoRepository formularioVersaoRepository;
    @Inject PerguntaVersaoRepository perguntaVersaoRepository;
    @Inject OpcaoPerguntaRepository opcaoPerguntaRepository;

    @Inject
    AvaliacaoRepository avaliacaoRepository;
    
    @Inject
    ValidacaoService validacaoService;

    @Inject
    VerificacaoEmailService verificacaoEmailService;



    @Inject
    QRCodeService qrCodeService;

    @Transactional
    public void salvar(FormularioDTO formularioDTO) {
        salvarFormulario(formularioDTO);
    }

    @Transactional
    public Validacao salvarEGerarHash(FormularioDTO formularioDTO) {
        salvarFormulario(formularioDTO);
        String codigoValidacao = qrCodeService.codificar(formularioDTO);
        Validacao validacao = validacaoService.armazenarCodigoValidacao(
                codigoValidacao,
                formularioDTO.respondent.aceiteTermosCondicoesServico
        );
        validacao.setCodigoValidacao(codigoValidacao);
        return validacao;
    }

    private void salvarFormulario(FormularioDTO formularioDTO) {
        validarFormulario(formularioDTO);
        PublicoAvaliacao publico = PublicoAvaliacao.from(formularioDTO.respondent.type);
        verificacaoEmailService.consumirAutorizacaoParaEnvio(
                formularioDTO.respondent.emailVerificationToken,
                publico,
                formularioDTO.campaign,
                formularioDTO.form,
                formularioDTO.formVersion
        );
        FormularioVersao formularioVersao = resolverFormularioVersao(formularioDTO, publico);
        if (formularioVersao.getEscopo() == EscopoFormulario.DISCIPLINA) {
            salvarPorDisciplina(formularioDTO, publico, formularioVersao);
        } else {
            salvarGeral(formularioDTO, publico, formularioVersao);
        }
        avaliacaoRepository.flush();
    }

    private void validarFormulario(FormularioDTO formularioDTO) {
        if (formularioDTO == null) {
            throw new IllegalArgumentException("Formulario nao pode ser vazio");
        }

        if (formularioDTO.campaign == null || formularioDTO.campaign.isBlank() || formularioDTO.form == null || formularioDTO.form.isBlank() || formularioDTO.formVersion == null)
            throw new IllegalArgumentException("Campanha, formulario e versao sao obrigatorios");

        if (formularioDTO.respondent == null) {
            throw new IllegalArgumentException("Dados do respondente sao obrigatorios");
        }

        PublicoAvaliacao.from(formularioDTO.respondent.type);

        if (!Boolean.TRUE.equals(formularioDTO.respondent.aceiteTermosCondicoesServico)) {
            throw new IllegalArgumentException("Aceite dos termos e condicoes de servico e obrigatorio");
        }

    }

    private void validarSubject(SubjectDTO subjectDTO) {
        if (subjectDTO == null) {
            throw new IllegalArgumentException("Disciplina nao pode ser vazia");
        }

        if (subjectDTO.subjectId == null || subjectDTO.subjectId.isBlank())
            throw new IllegalArgumentException("Identificador da disciplina e obrigatorio");

        if (subjectDTO.answers == null || subjectDTO.answers.isEmpty()) {
            throw new IllegalArgumentException("Ao menos uma resposta deve ser informada");
        }
    }

    private void validarResposta(RespostaDTO respostaDTO) {
        if (respostaDTO == null) {
            throw new IllegalArgumentException("Resposta nao pode ser vazia");
        }

        if (respostaDTO.questionId == null || respostaDTO.questionId.isBlank())
            throw new IllegalArgumentException("Identificador da pergunta e obrigatorio");
        if (respostaDTO.optionCode == null || respostaDTO.optionCode.isBlank())
            throw new IllegalArgumentException("Codigo da opcao e obrigatorio");
    }

    private FormularioVersao resolverFormularioVersao(FormularioDTO dto, PublicoAvaliacao publico) {
        FormularioVersao versao = formularioVersaoRepository.findPublicada(dto.campaign, dto.form, publico, dto.formVersion);
        if (versao == null) throw new IllegalArgumentException("Formulario publicado nao encontrado para campanha aberta e publico informado");
        return versao;
    }

    private void preencherRespostaVersionada(Resposta resposta, RespostaDTO dto, FormularioVersao versao) {
        String codigo = dto.questionId;
        PerguntaVersao pergunta = perguntaVersaoRepository.findByVersionAndCode(versao.getId(), codigo);
        if (pergunta == null || !pergunta.isAtivo()) throw new IllegalArgumentException("Pergunta nao pertence ao formulario informado");
        if (dto.questionText != null && !dto.questionText.isBlank() && !dto.questionText.equals(pergunta.getTexto()))
            throw new IllegalArgumentException("Texto da pergunta nao corresponde a versao informada");
        OpcaoPergunta opcao = opcaoPerguntaRepository.findByQuestionAndCode(pergunta.getId(), dto.optionCode);
        if (opcao == null || !opcao.isAtivo()) throw new IllegalArgumentException("Opcao de resposta invalida para a pergunta informada");
        resposta.setPerguntaVersao(pergunta);
        resposta.setOpcaoCodigo(opcao.getCodigo());
        resposta.setOpcaoRotulo(opcao.getRotulo());
        resposta.setNaoSeiResponder(opcao.isNaoSeiResponder());
        resposta.setNota(opcao.getValor());
    }

    private String sanitizarComentario(String comentario, FormularioVersao versao) {
        if (comentario == null || comentario.isBlank()) return null;
        if (!versao.isComentarioPermitido())
            throw new IllegalArgumentException("Comentarios nao sao permitidos neste formulario");
        String limpo = comentario.replaceAll("<[^>]*>", "").trim();
        if (limpo.length() > 1000) throw new IllegalArgumentException("Comentario deve ter no maximo 1000 caracteres");
        return limpo;
    }
    private void salvarPorDisciplina(FormularioDTO dto, PublicoAvaliacao publico, FormularioVersao versao) {
        if (dto.course == null || dto.course.name == null || dto.course.name.isBlank())
            throw new IllegalArgumentException("Curso e obrigatorio para este formulario");
        if (dto.subjects == null || dto.subjects.isEmpty())
            throw new IllegalArgumentException("Ao menos uma disciplina deve ser informada");
        Curso curso = cursoRepository.findByNome(dto.course.name);
        if (curso == null || !curso.isAtivo()) throw new IllegalArgumentException("Curso informado nao existe");
        for (SubjectDTO subject : dto.subjects) {
            validarSubject(subject);
            Disciplina disciplina;
            try { disciplina = disciplinaRepository.findByIdAndCurso(Long.parseLong(subject.subjectId), curso.getId()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Identificador da disciplina invalido"); }
            if (disciplina == null || !disciplina.isAtivo()) throw new IllegalArgumentException("Disciplina informada nao existe para o curso");
            persistirAvaliacao(publico, versao, disciplina, subject.answers, subject.comment);
        }
    }

    private void salvarGeral(FormularioDTO dto, PublicoAvaliacao publico, FormularioVersao versao) {
        if (dto.answers == null || dto.answers.isEmpty())
            throw new IllegalArgumentException("Ao menos uma resposta deve ser informada");
        persistirAvaliacao(publico, versao, null, dto.answers, dto.comment);
    }

    private void persistirAvaliacao(PublicoAvaliacao publico, FormularioVersao versao, Disciplina disciplina,
                                    List<RespostaDTO> respostasDto, String comentario) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setDisciplina(disciplina);
        avaliacao.setPublico(publico);
        avaliacao.setFormularioVersao(versao);
        avaliacao.setComentariosGerais(sanitizarComentario(comentario, versao));
        List<Resposta> respostas = new ArrayList<>();
        for (RespostaDTO dto : respostasDto) {
            validarResposta(dto);
            Resposta resposta = new Resposta();
            resposta.setAvaliacao(avaliacao);
            preencherRespostaVersionada(resposta, dto, versao);
            respostas.add(resposta);
        }
        avaliacao.setRespostas(respostas);
        avaliacaoRepository.persist(avaliacao);
    }
}
