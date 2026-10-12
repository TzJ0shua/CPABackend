package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.femass.entity.Avaliacao;
import org.femass.entity.PublicoAvaliacao;

import java.util.List;

@ApplicationScoped
public class AvaliacaoRepository implements PanacheRepositoryBase<Avaliacao, Long> {

    public List<Avaliacao> findByValidacao(Long id) {
        return find("select distinct a from Avaliacao a "
                + "left join fetch a.disciplina d left join fetch d.curso "
                + "left join fetch a.respostas r where a.validacao.id = ?1 or exists "
                + "(select j.id from JornadaAluno j join j.participacoes p where j.comprovante.id = ?1 and p.id = a.validacao.id) order by a.id, r.id", id).list();
    }

    public List<Avaliacao> findByPublico(PublicoAvaliacao publico) {
        return find("select distinct a from Avaliacao a "
                + "left join fetch a.disciplina d "
                + "left join fetch d.curso "
                + "left join fetch a.respostas r "
                + "left join fetch r.pergunta "
                + "left join fetch r.perguntaVersao "
                + "where a.publico = ?1 order by a.id, r.id", publico).list();
    }
}
