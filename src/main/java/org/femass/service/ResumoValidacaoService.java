package org.femass.service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.femass.dto.ResumoValidacaoDTO;
import org.femass.repository.ValidacaoRepository;
import org.femass.repository.AvaliacaoRepository;
import org.femass.entity.Resposta;

@ApplicationScoped
public class ResumoValidacaoService {
    @Inject ValidacaoRepository validacoes;
    @Inject AvaliacaoRepository avaliacoes;
    @Transactional
    public ResumoValidacaoDTO consultar(Long id) {
        var validacao = validacoes.findById(id);
        var versao = validacao.getFormularioVersao();
        var lista = avaliacoes.findByValidacao(id).stream().map(a -> {
            var d = a.getDisciplina();
            return new ResumoValidacaoDTO.Avaliacao(a.getId(), d == null ? null : new ResumoValidacaoDTO.Curso(d.getCurso().getId(), d.getCurso().getNome()),
                d == null ? null : new ResumoValidacaoDTO.Disciplina(d.getId(), d.getNome(), d.getProfessor()),
                a.getRespostas().stream().map(Resposta::getId).sorted().toList());
        }).toList();
        if (versao == null) return new ResumoValidacaoDTO(id, null, null, lista);
        var form = versao.getFormulario();
        var campanha = form.getCampanha();
        return new ResumoValidacaoDTO(id, new ResumoValidacaoDTO.Campanha(campanha.getCodigo(), campanha.getNome()),
            new ResumoValidacaoDTO.Formulario(form.getCodigo(), versao.getNome(), versao.getNumero(), versao.getPublico().getValor(), versao.getEscopo().name()), lista);
    }
}
