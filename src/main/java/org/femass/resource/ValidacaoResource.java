package org.femass.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.femass.dto.*;
import org.femass.entity.Validacao;
import org.femass.service.ValidacaoService;

import java.util.List;

@ApplicationScoped
@Path("/validacao")
public class ValidacaoResource {

    @Inject
    ValidacaoService service;
    @Inject org.femass.service.ResumoValidacaoService resumos;

    @POST
    @Path("/armazenar-codigo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response armazenarCodigo(String codigo) {
        Validacao validacao = service.armazenarCodigoValidacao(codigo);
        ValidacaoResponseDTO response = new ValidacaoResponseDTO(
            "Codigo recebido com sucesso!",
            "codigo",
            codigo,
            codigo
        );
        return Response.ok(response, MediaType.APPLICATION_JSON).build();
    }

    @PUT
    @Path("/validar-hash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response validarHash(@QueryParam("codigoValidacao") String codigoValidacao,
                                @QueryParam("hash") String hashLegado) {
        String codigo = resolverCodigoValidacao(codigoValidacao, hashLegado);
        if (codigo == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponseDTO("Codigo de validacao e obrigatorio"))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }


        try {
            Validacao validacao = service.validarCodigo(codigo);
            return Response.ok(statusComResumo(validacao, codigo), MediaType.APPLICATION_JSON).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponseDTO(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponseDTO(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponseDTO("Erro ao validar codigo"))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    @PUT
    @Path("/validar-hash/detalhado")
    @Produces(MediaType.APPLICATION_JSON)
    public Response validarHashDetalhado(@QueryParam("codigoValidacao") String codigoValidacao,
                                         @QueryParam("hash") String hashLegado) {
        String codigo = resolverCodigoValidacao(codigoValidacao, hashLegado);
        if (codigo == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponseDTO("Codigo de validacao e obrigatorio"))
                    .build();
        }

        try {
            Validacao validacao = service.validarCodigo(codigo);
            long tempoDecorrido = java.time.temporal.ChronoUnit.MILLIS.between(
                validacao.getDataCriacao(),
                validacao.getDataValidacao()
            );

            ValidacaoDetailResponseDTO response = new ValidacaoDetailResponseDTO(
                true,
                String.valueOf(validacao.getId()),
                codigo,
                validacao.getValidado(),
                validacao.getDataCriacao(),
                validacao.getDataValidacao(),
                validacao.getTentativasValidacao(),
                tempoDecorrido
            );
            response.setResumo(resumos.consultar(validacao.getId()));
            return Response.ok(response, MediaType.APPLICATION_JSON).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponseDTO(e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponseDTO(e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponseDTO("Erro ao validar codigo"))
                    .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    @GET
    @Path("/verificar-status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response verificarStatus(@QueryParam("codigoValidacao") String codigoValidacao,
                                    @QueryParam("hash") String hashLegado) {
        String codigo = resolverCodigoValidacao(codigoValidacao, hashLegado);
        if (codigo == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponseDTO("Codigo de validacao e obrigatorio"))
                .build();
        }

        try {
            Boolean validado = service.verificarStatusValidacao(codigo);
            String status = validado ? "VALIDADO" : "PENDENTE";
            String mensagem = validado
                ? "Este hash foi validado com sucesso"
                : "Este hash ainda está pendente de validação";

            ValidacaoStatusDTO response = new ValidacaoStatusDTO(codigo, validado, status, mensagem);
            return Response.ok(response, MediaType.APPLICATION_JSON).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponseDTO(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponseDTO("Erro ao verificar status"))
                .build();
        }
    }

    @GET
    @Path("/buscar-hash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response buscarHash(@QueryParam("codigoValidacao") String codigoValidacao,
                               @QueryParam("hash") String hashLegado) {
        String codigo = resolverCodigoValidacao(codigoValidacao, hashLegado);
        if (codigo == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponseDTO("Codigo de validacao e obrigatorio"))
                .build();
        }

        try {
            Validacao validacao = service.buscarCodigo(codigo);
            ValidacaoResponseDTO response = new ValidacaoResponseDTO(
                "Codigo encontrado com sucesso!",
                String.valueOf(validacao.getId()),
                codigo,
                validacao.getValidado() ? "validado" : "pendente"
            );
            return Response.ok().entity(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponseDTO(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponseDTO("Erro ao buscar codigo"))
                .build();
        }
    }

    private String resolverCodigoValidacao(String codigoValidacao, String hashLegado) {
        if (codigoValidacao != null && !codigoValidacao.isBlank()) {
            return codigoValidacao;
        }
        if (hashLegado != null && !hashLegado.isBlank()) {
            return hashLegado;
        }
        return null;
    }
    @GET
    @Path("historico")
    @Produces(MediaType.APPLICATION_JSON)
    public Response historico() {
       try{
           List<Validacao> historico = service.buscarDezUltimosCodigosValidados();
           return Response.ok(historico.stream().map(v -> statusComResumo(v, "codigo")).toList(), MediaType.APPLICATION_JSON).build();
       }catch (IllegalArgumentException e) {
           return Response.status(Response.Status.NOT_FOUND)
                   .entity(new ErrorResponseDTO(e.getMessage()))
                   .build();
       } catch (Exception e) {
           return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                   .entity(new ErrorResponseDTO("Erro ao buscar historico"))
                   .build();
       }

    }
    private String codigoDigestFinal(Validacao validacao) {
        String digest = validacao.getCodigoDigest();
        return digest.substring(digest.length() - 10);
    }
    private ValidacaoStatusDTO statusComResumo(Validacao validacao, String codigo) {
        var dto = new ValidacaoStatusDTO(codigo, true, "VALIDADO", "Codigo validado com sucesso", codigoDigestFinal(validacao));
        dto.setResumo(resumos.consultar(validacao.getId()));
        return dto;
    }
}
