package org.femass;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.femass.entity.EstadoCampanha;
import org.femass.repository.CampanhaRepository;
import org.femass.service.DisponibilidadeCampanhaService;

/** Schedule the seeded catalog explicitly; production data stays unscheduled after V14. */
@ApplicationScoped
public class CampaignTestFixtures {
    @Inject CampanhaRepository repository;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    @Transactional public void scheduleSeed() {
        var c = repository.findByCodigo("cpa-2026");
        c.setAtiva(true); c.setEstado(EstadoCampanha.ABERTA);
        c.setDataInicio(disponibilidade.hoje()); c.setDataFim(disponibilidade.hoje());
    }
    @Transactional public void clearSeed() {
        var c = repository.findByCodigo("cpa-2026");
        c.setAtiva(true); c.setEstado(EstadoCampanha.ABERTA);
        c.setDataInicio(null); c.setDataFim(null);
    }
}
