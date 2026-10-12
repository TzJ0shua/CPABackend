package org.femass.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "AVALIACAO")
public class Avaliacao {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validacao_id")
    private Validacao validacao;
    public Validacao getValidacao() { return validacao; }
    public void setValidacao(Validacao value) { validacao = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina; // Disciplina avaliada

    @ManyToOne
    @JoinColumn(name = "formulario_versao_id")
    private FormularioVersao formularioVersao;

    @Enumerated(EnumType.STRING)
    @Column(name = "PUBLICO", nullable = false, length = 20)
    private PublicoAvaliacao publico = PublicoAvaliacao.ALUNO;

    @OneToMany(mappedBy = "avaliacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resposta> respostas; // Respostas das perguntas

    @Column(name = "comentario", length = 1000)
    private String comentariosGerais; // Comentários adicionais

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Disciplina getDisciplina() {
        return disciplina;
    }

    public void setDisciplina(Disciplina disciplina) {
        this.disciplina = disciplina;
    }

    public FormularioVersao getFormularioVersao() { return formularioVersao; }
    public void setFormularioVersao(FormularioVersao formularioVersao) { this.formularioVersao = formularioVersao; }

    public PublicoAvaliacao getPublico() {
        return publico;
    }

    public void setPublico(PublicoAvaliacao publico) {
        this.publico = publico;
    }

    public List<Resposta> getRespostas() {
        return respostas;
    }

    public void setRespostas(List<Resposta> respostas) {
        this.respostas = respostas;
    }

    public String getComentariosGerais() {
        return comentariosGerais;
    }

    public void setComentariosGerais(String comentariosGerais) {
        this.comentariosGerais = comentariosGerais;
    }
}
