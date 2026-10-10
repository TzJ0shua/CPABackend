package org.femass.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "OPCAO_PERGUNTA", uniqueConstraints = @UniqueConstraint(columnNames = {"pergunta_versao_id", "codigo"}))
public class OpcaoPergunta {
    @Column(nullable = false)
    private boolean ativo = true;
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "pergunta_versao_id")
    private PerguntaVersao pergunta;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String rotulo;

    // Matches the existing schema: "não sei responder" has no numeric value.
    @Column
    private Integer valor;

    @Column(name = "NAO_SEI_RESPONDER", nullable = false)
    private boolean naoSeiResponder;

    @Column(nullable = false)
    private Integer ordem;

    public Long getId() { return id; }
    public PerguntaVersao getPergunta() { return pergunta; }
    public void setPergunta(PerguntaVersao pergunta) { this.pergunta = pergunta; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getRotulo() { return rotulo; }
    public void setRotulo(String rotulo) { this.rotulo = rotulo; }
    public Integer getValor() { return valor; }
    public void setValor(Integer valor) { this.valor = valor; }
    public boolean isNaoSeiResponder() { return naoSeiResponder; }
    public void setNaoSeiResponder(boolean naoSeiResponder) { this.naoSeiResponder = naoSeiResponder; }
    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
}
