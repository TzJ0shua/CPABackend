package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.dto.FormularioCatalogoDTO;
import org.femass.dto.OpcaoCatalogoDTO;
import org.femass.dto.PerguntaCatalogoDTO;
import org.femass.entity.FormularioVersao;
import org.femass.entity.OpcaoPergunta;
import org.femass.entity.PerguntaVersao;
import org.femass.entity.PublicoAvaliacao;
import org.femass.repository.FormularioVersaoRepository;
import org.femass.repository.OpcaoPerguntaRepository;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class FormularioCatalogoService {
    @Inject FormularioVersaoRepository versaoRepository;
    @Inject OpcaoPerguntaRepository opcaoRepository;
    @Inject EntityManager entityManager;

    public List<FormularioCatalogoDTO> buscar(String campanha, PublicoAvaliacao publico) {
        return versaoRepository.findCatalogo(campanha, publico).stream().map(this::toDto).toList();
    }

    public List<PerguntaCatalogoDTO> buscarPerguntas(String campanha, PublicoAvaliacao publico, String formulario, Integer versao) {
        FormularioVersao encontrada = versaoRepository.findPublicada(campanha, formulario, publico, versao);
        if (encontrada == null) throw new IllegalArgumentException("Formulario publicado nao encontrado para campanha aberta e publico informado");
        return buscarPerguntas(encontrada);
    }

    private FormularioCatalogoDTO toDto(FormularioVersao versao) {
        List<PerguntaCatalogoDTO> perguntas = buscarPerguntas(versao);
        return new FormularioCatalogoDTO(versao.getFormulario().getCampanha().getCodigo(),
                versao.getFormulario().getCodigo(), versao.getNome(),
                versao.getPublico().getValor(), versao.getNumero(), versao.getOrdem(),
                versao.getEscopo().name(), versao.isComentarioPermitido(), versao.getAvisoComentario(), perguntas);
    }

    private List<PerguntaCatalogoDTO> buscarPerguntas(FormularioVersao versao) {
        return versao.getFormulario().getId() == null ? List.of() :
                entityManager.createQuery(
                        "from PerguntaVersao p where p.formularioVersao.id = :id and p.ativo = true order by p.ordem", PerguntaVersao.class)
                        .setParameter("id", versao.getId()).getResultList().stream()
                        .map(p -> new PerguntaCatalogoDTO(p.getCodigo(), p.getTexto(), p.getOrdem(),
                                opcaoRepository.find("pergunta.id = ?1 and ativo = true order by ordem", p.getId()).list().stream()
                                        .sorted(Comparator.comparing(OpcaoPergunta::getOrdem))
                                        .map(o -> new OpcaoCatalogoDTO(o.getCodigo(), o.getRotulo(), o.getValor(), o.isNaoSeiResponder())).toList()))
                        .toList();
    }
}
