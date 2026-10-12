package org.femass.dto;
import java.util.List;

public record ResumoValidacaoDTO(Long validacaoId, Campanha campanha, Formulario formulario, List<Avaliacao> avaliacoes) {
    public record Campanha(String codigo, String nome) { }
    public record Formulario(String codigo, String nome, Integer versao, String publico, String escopo) { }
    public record Curso(Long id, String nome) { }
    public record Disciplina(Long id, String nome, String professor) { }
    public record Avaliacao(Long id, Curso curso, Disciplina disciplina, List<Long> respostaIds, Formulario formulario) { }
}
