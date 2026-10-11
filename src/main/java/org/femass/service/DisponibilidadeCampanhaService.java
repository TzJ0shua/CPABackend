package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.femass.entity.Campanha;
import org.femass.entity.EstadoCampanha;
import org.femass.repository.CampanhaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@ApplicationScoped
public class DisponibilidadeCampanhaService {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    @Inject Clock clock;
    @Inject CampanhaRepository repository;

    public LocalDate hoje() { return LocalDate.now(clock.withZone(ZONE)); }

    public boolean disponivel(Campanha c) {
        LocalDate hoje = hoje();
        return c != null && c.isAtiva() && c.getEstado() == EstadoCampanha.ABERTA
            && c.getDataInicio() != null && c.getDataFim() != null
            && !hoje.isBefore(c.getDataInicio()) && !hoje.isAfter(c.getDataFim());
    }

    public Campanha atual() {
        return repository.find("ativa = true and estado = 'ABERTA' and dataInicio <= ?1 and dataFim >= ?1 order by id", hoje()).firstResult();
    }

    public void exigirDisponivel(String codigo) {
        if (!disponivel(repository.findByCodigo(codigo)))
            throw new CampanhaIndisponivelException("A campanha não está disponível para respostas no período atual.");
    }
}
