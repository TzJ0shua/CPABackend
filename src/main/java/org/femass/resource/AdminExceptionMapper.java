package org.femass.resource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.femass.exception.AdminException;
import java.util.Map;

@Provider
public class AdminExceptionMapper implements ExceptionMapper<AdminException> {
    public Response toResponse(AdminException error) {
        return Response.status(error.getStatus()).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", error.getMessage())).build();
    }
}
