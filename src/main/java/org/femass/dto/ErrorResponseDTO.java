package org.femass.dto;

public class ErrorResponseDTO {
    private String error;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String code;
    public String getCode() { return code; }
    public ErrorResponseDTO(String error, String code) { this.error = error; this.code = code; }
    public ErrorResponseDTO() {}
    public ErrorResponseDTO(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

