package org.femass.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.femass.entity.Curso;

import java.util.List;

@ApplicationScoped
public class CursoRepository implements PanacheRepositoryBase<Curso, Long> {

    public Curso findByNome(String nome) {
        return find("nome", nome).firstResult();
    }

    public List<Curso> findAllOrderedByNome() {
        return find("ativo = true order by nome").list();
    }
}
