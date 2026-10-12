package org.femass.resource;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Map;

public final class AdminHttpExceptionMappers {
    private AdminHttpExceptionMappers() {}
    static Response error(int status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("message", message)).build();
    }
    @Provider public static class Unauthorized implements ExceptionMapper<UnauthorizedException> {
        public Response toResponse(UnauthorizedException e) {
            return Response.fromResponse(error(401, "Autenticação necessária.")).header("WWW-Authenticate", "Bearer").build();
        }
    }
    @Provider public static class Forbidden implements ExceptionMapper<ForbiddenException> {
        public Response toResponse(ForbiddenException e) { return error(403, "Acesso negado. É necessário o perfil cpa-admin."); }
    }
    @Provider public static class Constraint implements ExceptionMapper<ConstraintViolationException> {
        public Response toResponse(ConstraintViolationException e) { return error(409, "O cadastro conflita com um registro ou vínculo existente."); }
    }
    @Provider public static class Http implements ExceptionMapper<WebApplicationException> {
        @Context UriInfo uri;
        public Response toResponse(WebApplicationException e) {
            if (!uri.getPath().startsWith("admin/")) return e.getResponse();
            int status = e.getResponse().getStatus();
            return error(status, switch (status) {
                case 400 -> "Requisição inválida. Verifique os campos enviados.";
                case 401 -> "Autenticação necessária.";
                case 403 -> "Acesso negado.";
                case 404 -> "Recurso não encontrado.";
                default -> "Não foi possível concluir a operação.";
            });
        }
    }
}
