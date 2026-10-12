package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.dto.AdminViews;
import org.femass.entity.Campanha;

/** Deletes only campaign-owned data, in foreign-key order, inside the caller's transaction. */
@ApplicationScoped
public class ExclusaoCampanhaService {
    @Inject EntityManager em;

    private static final String FORMS = "(SELECT id FROM formulario WHERE campanha_id = :id)";
    private static final String VERSIONS = "(SELECT id FROM formulario_versao WHERE formulario_id IN " + FORMS + ")";
    private static final String QUESTIONS = "(SELECT id FROM pergunta_versao WHERE formulario_versao_id IN " + VERSIONS + ")";
    private static final String VALIDATIONS = "(SELECT id FROM validacao WHERE formulario_versao_id IN " + VERSIONS + ")";
    private static final String EVALUATIONS = "(SELECT id FROM avaliacao WHERE formulario_versao_id IN " + VERSIONS
        + " OR validacao_id IN " + VALIDATIONS + ")";
    private static final String ANSWERS = "avaliacao_id IN " + EVALUATIONS + " OR pergunta_versao_id IN " + QUESTIONS;

    public AdminViews.ExclusaoCampanha resumo(Campanha c) {
        return new AdminViews.ExclusaoCampanha(c.getId(), c.getCodigo(), c.getNome(),
            count("formulario", "campanha_id = :id", c),
            count("formulario_versao", "formulario_id IN " + FORMS, c),
            count("pergunta_versao", "formulario_versao_id IN " + VERSIONS, c),
            count("opcao_pergunta", "pergunta_versao_id IN " + QUESTIONS, c),
            count("avaliacao", "id IN " + EVALUATIONS, c), count("resposta", ANSWERS, c),
            count("validacao", "formulario_versao_id IN " + VERSIONS, c),
            ((Number) em.createNativeQuery("SELECT count(*) FROM verificacao_email WHERE campanha = :codigo")
                .setParameter("codigo", c.getCodigo()).getSingleResult()).longValue());
    }

    private long count(String table, String condition, Campanha c) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table + " WHERE " + condition)
            .setParameter("id", c.getId()).getSingleResult()).longValue();
    }

    public void excluirConteudo(Campanha c) {
        delete("resposta", ANSWERS, c);
        delete("avaliacao", "id IN " + EVALUATIONS, c);
        delete("validacao", "formulario_versao_id IN " + VERSIONS, c);
        em.createNativeQuery("DELETE FROM verificacao_email WHERE campanha = :codigo")
            .setParameter("codigo", c.getCodigo()).executeUpdate();
        delete("opcao_pergunta", "pergunta_versao_id IN " + QUESTIONS, c);
        delete("pergunta_versao", "formulario_versao_id IN " + VERSIONS, c);
        delete("formulario_versao", "formulario_id IN " + FORMS, c);
        delete("formulario", "campanha_id = :id", c);
    }

    private void delete(String table, String condition, Campanha c) {
        em.createNativeQuery("DELETE FROM " + table + " WHERE " + condition)
            .setParameter("id", c.getId()).executeUpdate();
    }
}
