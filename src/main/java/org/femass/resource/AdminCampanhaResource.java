package org.femass.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.*;
import org.femass.service.AdministracaoCampanhaService;
import java.util.List;

@ApplicationScoped
@Path("/admin/campanhas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("cpa-admin")
public class AdminCampanhaResource {
    @Inject AdministracaoCampanhaService service;
    @GET public List<AdminViews.Campanha> listar() { return service.listar(); }
    @GET @Path("/{campaign}") public AdminViews.Campanha consultar(@PathParam("campaign") String c) { return service.consultar(c); }
    @POST public Response criar(AdminCampanhaDTO dto) {
        if (dto == null) throw AdministracaoCampanhaService.bad("Dados da campanha são obrigatórios");
        return Response.status(201).entity(service.view(service.criarCampanha(dto.codigo, dto.nome, dto.mensagem))).build();
    }
    @PUT @Path("/{campaign}") public void atualizarCampanha(@PathParam("campaign") String c, AdminCampanhaDTO dto) { service.atualizarCampanha(c, dto); }
    @PATCH @Path("/{campaign}/status") public void statusCampanha(@PathParam("campaign") String c, AdminViews.Status dto) { service.statusCampanha(c, dto); }
    @GET @Path("/{campaign}/formularios") public List<AdminViews.Formulario> formularios(@PathParam("campaign") String c) { return service.formularios(c); }
    @GET @Path("/{campaign}/formularios/{form}") public AdminViews.Formulario formulario(@PathParam("campaign") String c, @PathParam("form") String f) { return service.consultarFormulario(c, f); }
    @POST @Path("/{campaign}/formularios") public Response criarFormulario(@PathParam("campaign") String c, AdminFormularioDTO dto) {
        return Response.status(201).entity(service.criarFormulario(c, dto).getNumero()).build();
    }
    @PATCH @Path("/{campaign}/formularios/{form}/status") public void statusFormulario(@PathParam("campaign") String c, @PathParam("form") String f, AdminViews.Status dto) { service.statusFormulario(c, f, dto); }
    @GET @Path("/{campaign}/formularios/{form}/versoes") public List<AdminViews.Versao> versoes(@PathParam("campaign") String c, @PathParam("form") String f) { return service.versoes(c, f); }
    @POST @Path("/{campaign}/formularios/{form}/versoes") @Consumes(MediaType.WILDCARD) public Response clonar(@PathParam("campaign") String c, @PathParam("form") String f) {
        return Response.status(201).entity(service.clonarVersao(c, f).getNumero()).build();
    }
    @GET @Path("/{campaign}/formularios/{form}/versoes/{version}") public AdminViews.Versao versao(@PathParam("campaign") String c, @PathParam("form") String f, @PathParam("version") Integer n) { return service.consultarVersao(c, f, n); }
    @PUT @Path("/{campaign}/formularios/{form}/versoes/{version}") public void atualizar(@PathParam("campaign") String c, @PathParam("form") String f, @PathParam("version") Integer n, AdminFormularioDTO dto) { service.atualizarVersao(c, f, n, dto); }
    @PATCH @Path("/{campaign}/formularios/{form}/versoes/{version}/status") public void statusVersao(@PathParam("campaign") String c, @PathParam("form") String f, @PathParam("version") Integer n, AdminViews.Status dto) { service.statusVersao(c, f, n, dto); }
    @POST @Path("/{campaign}/formularios/{form}/versoes/{version}/publicar") @Consumes(MediaType.WILDCARD) public void publicar(@PathParam("campaign") String c, @PathParam("form") String f, @PathParam("version") Integer n) { service.publicarVersao(c, f, n); }
    @POST @Path("/{campaign}/aprovar") @Consumes(MediaType.WILDCARD) public void aprovar(@PathParam("campaign") String c) { service.aprovar(c); }
    @POST @Path("/{campaign}/abrir") @Consumes(MediaType.WILDCARD) public void abrir(@PathParam("campaign") String c) { service.abrir(c); }
    @POST @Path("/{campaign}/encerrar") @Consumes(MediaType.WILDCARD) public void encerrar(@PathParam("campaign") String c) { service.encerrar(c); }
}
