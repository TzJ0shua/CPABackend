package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.femass.entity.Disciplina;

import java.util.List;

@ApplicationScoped
public class DisciplinaRepository implements PanacheRepositoryBase<Disciplina, Long> {

    public Disciplina findByIdAndCurso(Long id, Long cursoId) {
        return find("id = ?1 and curso.id = ?2", id, cursoId).firstResult();
    }

    public Disciplina findByIdValue(String id) {
        return find("id", id).firstResult();
    }

    public List<Disciplina> findAllWithCursoOrdered() {
        return find("from Disciplina d join fetch d.curso where d.ativo = true and d.curso.ativo = true order by d.curso.nome, d.nome").list();
    }
}
