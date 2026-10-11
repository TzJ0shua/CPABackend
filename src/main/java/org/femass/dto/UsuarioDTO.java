package org.femass.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Equivalente ao Respondent do json
@JsonIgnoreProperties({"email"})
public class UsuarioDTO {
    public String cpf;
    public String matricula;
    public String type;
    public Boolean aceiteTermosCondicoesServico;
    /** Autorizacao opaca emitida apos validar o PIN de e-mail ou a chave mestra. */
    public String emailVerificationToken;
}
