package org.femass.dto;

import java.util.List;

public class AdminPerguntaDTO {
    public boolean ativo = true;
    public String codigo;
    public String texto;
    public Integer ordem;
    public List<AdminOpcaoDTO> opcoes;
}
