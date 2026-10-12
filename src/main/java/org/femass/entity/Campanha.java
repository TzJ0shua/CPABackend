package org.femass.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "CAMPANHA")
public class Campanha {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false)
    private boolean ativa = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCampanha estado = EstadoCampanha.RASCUNHO;

    @Column(name = "mensagem_disponibilidade", length = 500)
    private String mensagemDisponibilidade;

    @Column(name = "data_inicio")
    private java.time.LocalDate dataInicio;
    @Column(name = "data_fim")
    private java.time.LocalDate dataFim;

    public java.time.LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(java.time.LocalDate value) { dataInicio = value; }
    public java.time.LocalDate getDataFim() { return dataFim; }
    public void setDataFim(java.time.LocalDate value) { dataFim = value; }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public EstadoCampanha getEstado() { return estado; }
    public void setEstado(EstadoCampanha estado) { this.estado = estado; }
    public String getMensagemDisponibilidade() { return mensagemDisponibilidade; }
    public void setMensagemDisponibilidade(String mensagemDisponibilidade) { this.mensagemDisponibilidade = mensagemDisponibilidade; }
}
