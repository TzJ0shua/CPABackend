package org.femass.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "FORMULARIO", uniqueConstraints = @UniqueConstraint(columnNames = {"codigo", "campanha_id"}))
public class Formulario {
    @Column(nullable = false)
    private boolean ativo = true;
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 180)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicoAvaliacao publico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public PublicoAvaliacao getPublico() { return publico; }
    public void setPublico(PublicoAvaliacao publico) { this.publico = publico; }
    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }
}
