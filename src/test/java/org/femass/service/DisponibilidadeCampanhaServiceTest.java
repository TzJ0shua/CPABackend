package org.femass.service;

import org.femass.entity.Campanha;
import org.femass.entity.EstadoCampanha;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class DisponibilidadeCampanhaServiceTest {
    @Test void usaDataDeSaoPauloELimitesInclusivos() {
        var service = new DisponibilidadeCampanhaService();
        // UTC is already October 11; São Paulo is still October 10.
        service.clock = Clock.fixed(Instant.parse("2036-10-11T01:00:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2036, 10, 10), service.hoje());
        Campanha c = new Campanha(); c.setEstado(EstadoCampanha.ABERTA);
        LocalDate hoje = service.hoje();
        c.setDataInicio(hoje); c.setDataFim(hoje);
        assertTrue(service.disponivel(c));
        c.setDataFim(hoje.plusDays(1)); assertTrue(service.disponivel(c));
        c.setDataInicio(hoje.minusDays(1)); c.setDataFim(hoje); assertTrue(service.disponivel(c));
        c.setDataInicio(hoje.plusDays(1)); c.setDataFim(hoje.plusDays(2)); assertFalse(service.disponivel(c));
        c.setDataInicio(hoje.minusDays(2)); c.setDataFim(hoje.minusDays(1)); assertFalse(service.disponivel(c));
        c.setDataInicio(null); c.setDataFim(null); assertFalse(service.disponivel(c));
        c.setDataInicio(hoje); c.setDataFim(hoje); c.setAtiva(false); assertFalse(service.disponivel(c));
        c.setAtiva(true);
        for (var estado : new EstadoCampanha[]{EstadoCampanha.RASCUNHO, EstadoCampanha.APROVADA, EstadoCampanha.ENCERRADA}) {
            c.setEstado(estado); assertFalse(service.disponivel(c));
        }
        assertFalse(service.disponivel(null));
    }
}
