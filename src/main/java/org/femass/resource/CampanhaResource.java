package org.femass.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.DisponibilidadeCampanhaDTO;
import org.femass.entity.Campanha;
import org.femass.repository.CampanhaRepository;

@ApplicationScoped
@Path("/campanhas")
@Produces(MediaType.APPLICATION_JSON)
public class CampanhaResource {
    @Inject
    CampanhaRepository campanhaRepository;

    @Inject org.femass.service.DisponibilidadeCampanhaService disponibilidadeService;

    @GET
    @Path("/atual/disponibilidade")
    public Response atual() {
        Campanha c = disponibilidadeService.atual();
        if (c == null) return Response.ok(new DisponibilidadeCampanhaDTO(null, null, false,
                "Não há campanha disponível para respostas no momento.", null, null)).build();
        return disponibilidade(c.getCodigo());
    }

    @GET
    @Path("/{codigo}/disponibilidade")
    public Response disponibilidade(@PathParam("codigo") String codigo) {
        Campanha campanha = campanhaRepository.findByCodigo(codigo);
        if (campanha == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        DisponibilidadeCampanhaDTO dto = new DisponibilidadeCampanhaDTO(
                campanha.getCodigo(),
                campanha.getEstado(),
                disponibilidadeService.disponivel(campanha),
                campanha.getMensagemDisponibilidade(), campanha.getDataInicio(), campanha.getDataFim()
        );
        return Response.ok(dto).build();
    }
}
