package org.femass.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.femass.entity.*;
import org.femass.service.AdministracaoCampanhaService;
import java.util.*;

@Path("/admin/campanhas/{campaign}/resultados")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("cpa-admin")
public class AdminResultadosResource {
    @Inject EntityManager em;
    @Inject AdministracaoCampanhaService campanhas;

    public record Item(Long avaliacao, String participante, String publico, String formulario,
        String formularioNome, Integer versao, String curso, String disciplina, String professor,
        String perguntaId, String pergunta, Integer nota, String opcao, boolean naoSeiResponder, String comentario, boolean demonstracao) {}

    @GET @Transactional
    public List<Item> resultados(@PathParam("campaign") String campaign) {
        campanhas.consultar(campaign);
        var avaliacoes = em.createQuery("select distinct a from Avaliacao a join fetch a.formularioVersao v "
            + "join fetch v.formulario f left join fetch a.validacao left join fetch a.disciplina d "
            + "left join fetch d.curso left join fetch a.respostas r left join fetch r.perguntaVersao "
            + "left join fetch r.pergunta where f.campanha.codigo = :codigo order by a.id", Avaliacao.class)
            .setParameter("codigo", campaign).getResultList();
        Map<String, String> participantes = new LinkedHashMap<>();
        List<Item> items = new ArrayList<>();
        for (var a : avaliacoes) {
            var v = a.getFormularioVersao();
            var validacao = a.getValidacao();
            String identidade = validacao == null ? "avaliacao:" + a.getId()
                : validacao.getCpfHash() != null ? "aluno:" + validacao.getCpfHash()
                : validacao.getEmailDigest() != null ? a.getPublico() + ":" + validacao.getEmailDigest()
                : "validacao:" + validacao.getId();
            String participante = participantes.computeIfAbsent(identidade, k -> "Participante " + (participantes.size() + 1));
            var d = a.getDisciplina();
            for (var r : a.getRespostas()) {
                var p = r.getPerguntaVersao();
                items.add(new Item(a.getId(), participante, a.getPublico().getValor(), v.getFormulario().getCodigo(),
                    v.getNome() == null ? v.getFormulario().getNome() : v.getNome(), v.getNumero(),
                    d == null || d.getCurso() == null ? null : d.getCurso().getNome(),
                    d == null ? null : d.getNome(), d == null ? null : d.getProfessor(),
                    p != null ? p.getCodigo() : r.getPergunta() == null ? "legada" : r.getPergunta().getCodigo(),
                    p != null ? p.getTexto() : r.getPergunta() == null ? "Pergunta legada" : r.getPergunta().getTexto(),
                    r.getNota(), r.getOpcaoRotulo(), r.isNaoSeiResponder(), a.getComentariosGerais(),
                    validacao != null && validacao.getOldToken() != null
                        && validacao.getOldToken().startsWith("CPA_DEMO_RESULTADOS_20261019_")));
            }
        }
        return items;
    }
}
