package org.femass.dto;

import org.femass.entity.EstadoCampanha;

public record DisponibilidadeCampanhaDTO(
        String campanha,
        EstadoCampanha estado,
        boolean disponivelParaResposta,
        String mensagem,
        java.time.LocalDate dataInicio,
        java.time.LocalDate dataFim
) {
}
