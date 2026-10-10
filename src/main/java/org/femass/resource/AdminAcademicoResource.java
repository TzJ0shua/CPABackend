package org.femass.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.AdminViews;
import org.femass.service.AdministracaoAcademicaService;
import java.util.List;

@ApplicationScoped
@Path("/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("cpa-admin")
public class AdminAcademicoResource {
    @Inject AdministracaoAcademicaService service;
    @GET @Path("/cursos") public List<AdminViews.Curso> cursos() { return service.listarCursos(); }
    @GET @Path("/cursos/{id}") public AdminViews.Curso curso(@PathParam("id") Long id) { return service.consultarCurso(id); }
    @POST @Path("/cursos") public Response criarCurso(AdminViews.CursoInput dto) { return Response.status(201).entity(service.criarCurso(dto)).build(); }
    @PUT @Path("/cursos/{id}") public void atualizarCurso(@PathParam("id") Long id, AdminViews.CursoInput dto) { service.atualizarCurso(id, dto); }
    @PATCH @Path("/cursos/{id}/status") public void statusCurso(@PathParam("id") Long id, AdminViews.Status dto) { service.statusCurso(id, dto); }
    @GET @Path("/disciplinas") public List<AdminViews.Disciplina> disciplinas(@QueryParam("cursoId") Long cursoId) { return service.listarDisciplinas(cursoId); }
    @GET @Path("/disciplinas/{id}") public AdminViews.Disciplina disciplina(@PathParam("id") Long id) { return service.consultarDisciplina(id); }
    @POST @Path("/disciplinas") public Response criarDisciplina(AdminViews.DisciplinaInput dto) { return Response.status(201).entity(service.criarDisciplina(dto)).build(); }
    @PUT @Path("/disciplinas/{id}") public void atualizarDisciplina(@PathParam("id") Long id, AdminViews.DisciplinaInput dto) { service.atualizarDisciplina(id, dto); }
    @PATCH @Path("/disciplinas/{id}/status") public void statusDisciplina(@PathParam("id") Long id, AdminViews.Status dto) { service.statusDisciplina(id, dto); }
}
