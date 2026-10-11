package org.femass.dto;

import java.time.LocalDateTime;

public class ValidacaoDetailResponseDTO {
    private ResumoValidacaoDTO resumo;
    public ResumoValidacaoDTO getResumo() { return resumo; }
    public void setResumo(ResumoValidacaoDTO value) { resumo = value; }
	private Boolean codigoValido;
	private String id;
	private String codigoValidacao;
	private Boolean validado;
	private LocalDateTime dataCriacao;
	private LocalDateTime dataValidacao;
	private Integer tentativasValidacao;
	private Long tempoDecorridoMs;

	public ValidacaoDetailResponseDTO(Boolean codigoValido, String id, String codigoValidacao, Boolean validado,
									  LocalDateTime dataCriacao, LocalDateTime dataValidacao,
									  Integer tentativasValidacao, Long tempoDecorridoMs) {
		this.codigoValido = codigoValido;
		this.id = id;
		this.codigoValidacao = codigoValidacao;
		this.validado = validado;
		this.dataCriacao = dataCriacao;
		this.dataValidacao = dataValidacao;
		this.tentativasValidacao = tentativasValidacao;
		this.tempoDecorridoMs = tempoDecorridoMs;
	}

	public Boolean getCodigoValido() { return codigoValido; }
	public void setCodigoValido(Boolean codigoValido) { this.codigoValido = codigoValido; }
	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getCodigoValidacao() { return codigoValidacao; }
	public void setCodigoValidacao(String codigoValidacao) { this.codigoValidacao = codigoValidacao; }
	public Boolean getValidado() { return validado; }
	public void setValidado(Boolean validado) { this.validado = validado; }
	public LocalDateTime getDataCriacao() { return dataCriacao; }
	public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
	public LocalDateTime getDataValidacao() { return dataValidacao; }
	public void setDataValidacao(LocalDateTime dataValidacao) { this.dataValidacao = dataValidacao; }
	public Integer getTentativasValidacao() { return tentativasValidacao; }
	public void setTentativasValidacao(Integer tentativasValidacao) { this.tentativasValidacao = tentativasValidacao; }
	public Long getTempoDecorridoMs() { return tempoDecorridoMs; }
	public void setTempoDecorridoMs(Long tempoDecorridoMs) { this.tempoDecorridoMs = tempoDecorridoMs; }
}

