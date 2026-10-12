package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.femass.dto.AdminViews;
import org.femass.entity.*;
import org.femass.repository.*;
import java.util.List;
import static org.femass.service.AdministracaoCampanhaService.*;

@ApplicationScoped
public class AdministracaoAcademicaService {
    @Inject CursoRepository cursos;
    @Inject DisciplinaRepository disciplinas;
    public List<AdminViews.Curso> listarCursos() { return cursos.find("order by nome, id").list().stream().map(this::view).toList(); }
    public AdminViews.Curso consultarCurso(Long id) { return view(curso(id)); }
    private AdminViews.Curso view(Curso c) { return new AdminViews.Curso(c.getId(), c.getNome(), c.isAtivo()); }
    private Curso curso(Long id) { Curso c = cursos.findById(id); if (c == null) throw missing("Curso não encontrado"); return c; }
    @Transactional public AdminViews.Curso criarCurso(AdminViews.CursoInput dto) {
        Curso c = new Curso(); preencher(c, dto); cursos.persistAndFlush(c); return view(c);
    }
    @Transactional public void atualizarCurso(Long id, AdminViews.CursoInput dto) { preencher(curso(id), dto); }
    private void preencher(Curso c, AdminViews.CursoInput dto) {
        if (dto == null) throw bad("Dados do curso são obrigatórios");
        String nome = texto(dto.nome(), "Nome do curso", 255); Curso existente = cursos.findByNome(nome);
        if (existente != null && !existente.getId().equals(c.getId())) throw conflict("Nome de curso já existe, inclusive entre inativos");
        c.setNome(nome);
    }
    @Transactional public void statusCurso(Long id, AdminViews.Status dto) { curso(id).setAtivo(ativo(dto)); }
    public List<AdminViews.Disciplina> listarDisciplinas(Long cursoId) {
        if (cursoId != null) curso(cursoId);
        return (cursoId == null ? disciplinas.find("order by curso.nome, nome, id").list() : disciplinas.find("curso.id = ?1 order by nome, id", cursoId).list()).stream().map(this::view).toList();
    }
    public AdminViews.Disciplina consultarDisciplina(Long id) { return view(disciplina(id)); }
    private AdminViews.Disciplina view(Disciplina d) { return new AdminViews.Disciplina(d.getId(), d.getNome(), d.getProfessor(), d.getCurso().getId(), d.getCurso().getNome(), d.isAtivo(), d.getCurso().isAtivo()); }
    private Disciplina disciplina(Long id) { Disciplina d = disciplinas.findById(id); if (d == null) throw missing("Disciplina não encontrada"); return d; }
    @Transactional public AdminViews.Disciplina criarDisciplina(AdminViews.DisciplinaInput dto) {
        Disciplina d = new Disciplina(); preencher(d, dto); disciplinas.persistAndFlush(d); return view(d);
    }
    @Transactional public void atualizarDisciplina(Long id, AdminViews.DisciplinaInput dto) { preencher(disciplina(id), dto); }
    private void preencher(Disciplina d, AdminViews.DisciplinaInput dto) {
        if (dto == null || dto.cursoId() == null) throw bad("Curso é obrigatório");
        Curso c = curso(dto.cursoId());
        if (!c.isAtivo() && (d.getCurso() == null || !d.getCurso().getId().equals(c.getId()))) throw conflict("Não é possível vincular a um curso inativo");
        d.setNome(texto(dto.nome(), "Nome da disciplina", 255)); d.setProfessor(texto(dto.professor(), "Professor", 255)); d.setCurso(c);
    }
    @Transactional public void statusDisciplina(Long id, AdminViews.Status dto) { disciplina(id).setAtivo(ativo(dto)); }
}
