package org.femass.dto;

import java.util.List;

/** Administrative read models deliberately exclude JPA relationships. */
public final class AdminViews {
    private AdminViews() {}
    public record Status(Boolean ativo) {}
    public record CursoInput(String nome) {}
    public record DisciplinaInput(String nome, String professor, Long cursoId) {}
    public record Campanha(Long id, String codigo, String nome, String mensagem, String estado, boolean ativo) {}
    public record Curso(Long id, String nome, boolean ativo) {}
    public record Disciplina(Long id, String nome, String professor, Long cursoId, String cursoNome, boolean ativo, boolean cursoAtivo) {}
    public record Formulario(Long id, String codigo, String nome, String publico, boolean ativo) {}
    public record Versao(Long id, Integer numero, String codigo, String nome, String publico, String escopo,
                         Integer ordem, boolean commentAllowed, String commentNotice, String estado,
                         boolean ativo, List<Pergunta> perguntas) {}
    public record Pergunta(Long id, String codigo, String texto, Integer ordem, boolean ativo, List<Opcao> opcoes) {}
    public record Opcao(Long id, String code, String label, Integer value, boolean naoSeiResponder, boolean ativo) {}
}
