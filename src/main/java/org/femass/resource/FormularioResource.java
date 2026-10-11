package org.femass.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.ErrorResponseDTO;
import org.femass.dto.FormularioDTO;
import org.femass.dto.QRCodeResponseDTO;
import org.femass.entity.Validacao;
import org.femass.exception.InvalidFormularioException;
import org.femass.service.FormularioService;

@ApplicationScoped
@Path("/formulario")
public class FormularioResource {

    @Inject
    private FormularioService formularioService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response PostFormulario(FormularioDTO formularioDTO) {
        try {
            Validacao validacao = formularioService.salvarEGerarHash(formularioDTO);
            String digest = validacao.getCodigoDigest();
            return Response.ok(new QRCodeResponseDTO(validacao.getCodigoValidacao(), validacao.getCodigoValidacao(),
                digest.substring(digest.length() - 10))).build();
        } catch (org.femass.service.CampanhaIndisponivelException e) {
            return Response.status(409).entity(new ErrorResponseDTO(e.getMessage())).build();
        } catch (IllegalArgumentException | InvalidFormularioException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponseDTO(e.getMessage()))
                    .build();
        } catch (PersistenceException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponseDTO("Erro ao salvar formulario no banco de dados"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponseDTO("Erro inesperado ao processar formulario"))
                    .build();
        }
    }

}
