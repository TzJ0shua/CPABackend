package org.femass.dto;

public class ValidacaoStatusDTO {
    private ResumoValidacaoDTO resumo;
    public ResumoValidacaoDTO getResumo() { return resumo; }
    public void setResumo(ResumoValidacaoDTO value) { resumo = value; }
    private String codigoValidacao;
    private Boolean validado;
    private String status;
    private String mensagem;
    private String codigoDigestFinal;

    public ValidacaoStatusDTO(String codigoValidacao, Boolean validado, String status, String mensagem) {
        this.codigoValidacao = codigoValidacao;
        this.validado = validado;
        this.status = status;
        this.mensagem = mensagem;
    }

    public ValidacaoStatusDTO(String codigoValidacao, Boolean validado, String status, String mensagem,
                              String codigoDigestFinal) {
        this(codigoValidacao, validado, status, mensagem);
        this.codigoDigestFinal = codigoDigestFinal;
    }

    public String getCodigoDigestFinal() { return codigoDigestFinal; }

    public String getCodigoValidacao() { return codigoValidacao; }
    public void setCodigoValidacao(String codigoValidacao) { this.codigoValidacao = codigoValidacao; }

    public Boolean getValidado() {
        return validado;
    }

    public void setValidado(Boolean validado) {
        this.validado = validado;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}

