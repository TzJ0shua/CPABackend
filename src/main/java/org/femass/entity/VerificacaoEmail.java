package org.femass.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "VERIFICACAO_EMAIL")
public class VerificacaoEmail extends PanacheEntityBase {

    @Column(name = "acesso_chave_mestra", nullable = false)
    private boolean acessoChaveMestra;
    public boolean isAcessoChaveMestra() { return acessoChaveMestra; }
    public void setAcessoChaveMestra(boolean value) { acessoChaveMestra = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "EMAIL_DIGEST", nullable = false, length = 64)
    private String emailDigest;

    @Column(name = "CODIGO_DIGEST", nullable = false, length = 64)
    private String codigoDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "PUBLICO", nullable = false, length = 20)
    private PublicoAvaliacao publico;

    @Column(name = "CAMPANHA", nullable = false, length = 100)
    private String campanha;

    @Column(name = "FORMULARIO", nullable = false, length = 100)
    private String formulario;

    @Column(name = "VERSAO_FORMULARIO", nullable = false)
    private Integer versaoFormulario;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusVerificacaoEmail status;

    @Column(name = "DATA_CRIACAO", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "EXPIRA_EM", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "TENTATIVAS", nullable = false)
    private Integer tentativas;

    @Column(name = "REENVIOS", nullable = false)
    private Integer reenvios;

    @Column(name = "CODIGO_VALIDADO_EM")
    private LocalDateTime codigoValidadoEm;

    @Column(name = "AUTORIZACAO_DIGEST", length = 64, unique = true)
    private String autorizacaoDigest;

    @Column(name = "AUTORIZACAO_EXPIRA_EM")
    private LocalDateTime autorizacaoExpiraEm;

    @Column(name = "CONSUMIDO_EM")
    private LocalDateTime consumidoEm;

    @PrePersist
    void prePersist() {
        if (dataCriacao == null) dataCriacao = LocalDateTime.now();
        if (status == null) status = StatusVerificacaoEmail.PENDENTE;
        if (tentativas == null) tentativas = 0;
        if (reenvios == null) reenvios = 0;
    }

    public Long getId() { return id; }
    public String getEmailDigest() { return emailDigest; }
    public void setEmailDigest(String emailDigest) { this.emailDigest = emailDigest; }
    public String getCodigoDigest() { return codigoDigest; }
    public void setCodigoDigest(String codigoDigest) { this.codigoDigest = codigoDigest; }
    public PublicoAvaliacao getPublico() { return publico; }
    public void setPublico(PublicoAvaliacao publico) { this.publico = publico; }
    public String getCampanha() { return campanha; }
    public void setCampanha(String campanha) { this.campanha = campanha; }
    public String getFormulario() { return formulario; }
    public void setFormulario(String formulario) { this.formulario = formulario; }
    public Integer getVersaoFormulario() { return versaoFormulario; }
    public void setVersaoFormulario(Integer versaoFormulario) { this.versaoFormulario = versaoFormulario; }
    public StatusVerificacaoEmail getStatus() { return status; }
    public void setStatus(StatusVerificacaoEmail status) { this.status = status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public Integer getTentativas() { return tentativas; }
    public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }
    public Integer getReenvios() { return reenvios; }
    public void setReenvios(Integer reenvios) { this.reenvios = reenvios; }
    public LocalDateTime getCodigoValidadoEm() { return codigoValidadoEm; }
    public void setCodigoValidadoEm(LocalDateTime codigoValidadoEm) { this.codigoValidadoEm = codigoValidadoEm; }
    public String getAutorizacaoDigest() { return autorizacaoDigest; }
    public void setAutorizacaoDigest(String autorizacaoDigest) { this.autorizacaoDigest = autorizacaoDigest; }
    public LocalDateTime getAutorizacaoExpiraEm() { return autorizacaoExpiraEm; }
    public void setAutorizacaoExpiraEm(LocalDateTime autorizacaoExpiraEm) { this.autorizacaoExpiraEm = autorizacaoExpiraEm; }
    public LocalDateTime getConsumidoEm() { return consumidoEm; }
    public void setConsumidoEm(LocalDateTime consumidoEm) { this.consumidoEm = consumidoEm; }
}
