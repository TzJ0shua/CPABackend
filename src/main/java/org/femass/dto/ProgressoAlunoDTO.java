package org.femass.dto;

import java.util.List;

public record ProgressoAlunoDTO(List<Formulario> formularios, boolean concluida, QRCodeResponseDTO comprovante) {
    public record Formulario(String code, Integer version, boolean respondido) { }
    public record Consulta(String campaign, String cpf, String matricula, Boolean aceiteTermosCondicoesServico) { }
}
