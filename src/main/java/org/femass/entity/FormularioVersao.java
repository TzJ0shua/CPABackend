package org.femass.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "FORMULARIO_VERSAO", uniqueConstraints = @UniqueConstraint(columnNames = {"formulario_id", "numero"}))
public class FormularioVersao {
    @Column(nullable = false)
    private boolean ativo = true;
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "formulario_id")
    private Formulario formulario;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "COMENTARIO_PERMITIDO", nullable = false)
    private boolean comentarioPermitido;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PublicoAvaliacao publico;

    @Column(length = 180)
    private String nome;

    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscopoFormulario escopo = EscopoFormulario.GERAL;

    @Column(name = "aviso_comentario", length = 500)
    private String avisoComentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFormularioVersao estado = EstadoFormularioVersao.RASCUNHO;

    public Long getId() { return id; }
    public Formulario getFormulario() { return formulario; }
    public void setFormulario(Formulario formulario) { this.formulario = formulario; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public boolean isComentarioPermitido() { return comentarioPermitido; }
    public void setComentarioPermitido(boolean comentarioPermitido) { this.comentarioPermitido = comentarioPermitido; }
    public PublicoAvaliacao getPublico() { return publico; }
    public void setPublico(PublicoAvaliacao publico) { this.publico = publico; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
    public EscopoFormulario getEscopo() { return escopo; }
    public void setEscopo(EscopoFormulario escopo) { this.escopo = escopo; }
    public String getAvisoComentario() { return avisoComentario; }
    public void setAvisoComentario(String avisoComentario) { this.avisoComentario = avisoComentario; }
    public EstadoFormularioVersao getEstado() { return estado; }
    public void setEstado(EstadoFormularioVersao estado) { this.estado = estado; }
}
