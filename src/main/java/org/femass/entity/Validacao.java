package org.femass.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "VALIDACAO")
public class Validacao extends PanacheEntityBase {
    @Column(name = "email_digest", length = 64)
    private String emailDigest;
    public String getEmailDigest() { return emailDigest; }
    public void setEmailDigest(String value) { emailDigest = value; }
    @Column(name = "cpf_hash", length = 64)
    private String cpfHash;
    @Column(name = "matricula_hash", length = 64)
    private String matriculaHash;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formulario_versao_id")
    private FormularioVersao formularioVersao;
    public String getCpfHash() { return cpfHash; }
    public void setCpfHash(String value) { cpfHash = value; }
    public String getMatriculaHash() { return matriculaHash; }
    public void setMatriculaHash(String value) { matriculaHash = value; }
    public FormularioVersao getFormularioVersao() { return formularioVersao; }
    public void setFormularioVersao(FormularioVersao value) { formularioVersao = value; }
    @Transient
    private String codigoValidacao;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "NEW_HASH", unique = true)
    private UUID newHash;

    @Column(name = "OLD_TOKEN", length = 512)
    private String oldToken;

    @Column(name = "VALIDADO", nullable = false)
    private Boolean validado;

    @Column(name = "DATA_CRIACAO", nullable = true)
    private LocalDateTime dataCriacao;

    @Column(name = "EXPIRA_EM", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "DATA_VALIDACAO", nullable = true)
    private LocalDateTime dataValidacao;

    @Column(name = "TENTATIVAS_VALIDACAO", nullable = true)
    private Integer tentativasValidacao;

    @Column(name = "ACEITE_TERMOS_CONDICOES_SERVICO", nullable = false)
    private Boolean aceiteTermosCondicoesServico;

    @PrePersist
    public void prePersist() {
        if (this.dataCriacao == null) {
            this.dataCriacao = LocalDateTime.now();
        }
        if (this.validado == null) {
            this.validado = false;
        }
        if (this.tentativasValidacao == null) {
            this.tentativasValidacao = 0;
        }
        if (this.aceiteTermosCondicoesServico == null) {
            this.aceiteTermosCondicoesServico = false;
        }
    }

    public String getCodigoDigest() {
        return newHash == null ? null : newHash.toString();
    }

    public void setCodigoDigest(String codigoDigest) {
        this.newHash = codigoDigest == null ? null : UUID.fromString(codigoDigest);
    }

    public UUID getNewHash() { return newHash; }
    public void setNewHash(UUID newHash) { this.newHash = newHash; }
    public String getOldToken() { return oldToken; }
    public void setOldToken(String oldToken) { this.oldToken = oldToken; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoValidacao() { return codigoValidacao; }
    public void setCodigoValidacao(String codigoValidacao) { this.codigoValidacao = codigoValidacao; }

    /** Compatibilidade binária temporária; não deve ser exposto em respostas. */
    @Deprecated
    public String getHash() {
        return getCodigoDigest();
    }

    @Deprecated
    public void setHash(String hash) {
        setCodigoDigest(hash);
    }

    public Boolean getValidado() {
        return validado;
    }

    public void setValidado(Boolean validado) {
        this.validado = validado;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
    }

    public LocalDateTime getDataValidacao() {
        return dataValidacao;
    }

    public void setDataValidacao(LocalDateTime dataValidacao) {
        this.dataValidacao = dataValidacao;
    }

    public Integer getTentativasValidacao() {
        return tentativasValidacao;
    }

    public void setTentativasValidacao(Integer tentativasValidacao) {
        this.tentativasValidacao = tentativasValidacao;
    }

    public Boolean getAceiteTermosCondicoesServico() {
        return aceiteTermosCondicoesServico;
    }

    public void setAceiteTermosCondicoesServico(Boolean aceiteTermosCondicoesServico) {
        this.aceiteTermosCondicoesServico = aceiteTermosCondicoesServico;
    }
}

