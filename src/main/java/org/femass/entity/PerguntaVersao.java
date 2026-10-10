package org.femass.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "PERGUNTA_VERSAO", uniqueConstraints = @UniqueConstraint(columnNames = {"formulario_versao_id", "codigo"}))
public class PerguntaVersao {
    @Column(nullable = false)
    private boolean ativo = true;
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "formulario_versao_id")
    private FormularioVersao formularioVersao;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false)
    private Integer ordem;

    public Long getId() { return id; }
    public FormularioVersao getFormularioVersao() { return formularioVersao; }
    public void setFormularioVersao(FormularioVersao formularioVersao) { this.formularioVersao = formularioVersao; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
}
