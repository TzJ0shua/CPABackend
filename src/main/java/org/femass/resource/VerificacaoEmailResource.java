package org.femass.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.*;
import org.femass.service.EmailDeliveryException;
import org.femass.service.VerificacaoEmailService;

@ApplicationScoped
@Path("/verificacao-email")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VerificacaoEmailResource {
    @Inject VerificacaoEmailService service;

    @POST
    @Path("/solicitar")
    public Response solicitar(SolicitarVerificacaoEmailDTO dto) {
        try {
            Long id = service.solicitar(dto);
            return Response.accepted(new SolicitarVerificacaoEmailResponseDTO(id,
                    "Se o endereco informado for elegivel, um codigo sera enviado")).build();
        } catch (org.femass.service.CampanhaIndisponivelException e) {
            return Response.status(409).entity(new ErrorResponseDTO(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponseDTO(e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS).entity(new ErrorResponseDTO("Solicitacao indisponivel no momento")).build();
        } catch (EmailDeliveryException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ErrorResponseDTO("Servico de e-mail indisponivel no momento")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponseDTO("Nao foi possivel solicitar a verificacao")).build();
        }
    }

    @POST
    @Path("/confirmar")
    public Response confirmar(ConfirmarVerificacaoEmailDTO dto) {
        try {
            return Response.ok(new ConfirmarVerificacaoEmailResponseDTO(service.confirmar(dto))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponseDTO("Codigo de verificacao invalido ou expirado")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponseDTO("Nao foi possivel confirmar a verificacao")).build();
        }
    }
}
