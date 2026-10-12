package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.femass.entity.Campanha;
import org.femass.entity.EstadoCampanha;
import org.femass.repository.CampanhaRepository;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CampanhaResourceTest {
    @Inject
    CampanhaRepository campanhaRepository;

    @org.junit.jupiter.api.AfterEach
    @Transactional
    void cleanup() {
        campanhaRepository.update("estado = ?1 where codigo like ?2", EstadoCampanha.ENCERRADA, "status-%");
    }

    @Test
    void deveExporCampanhaAbertaComoDisponivelSemAutenticacao() {
        String codigo = criarCampanha(EstadoCampanha.ABERTA, "Responda agora.");

        given()
                .when().get("/campanhas/{codigo}/disponibilidade", codigo)
                .then()
                .statusCode(200)
                .body("campanha", equalTo(codigo))
                .body("estado", equalTo("ABERTA"))
                .body("disponivelParaResposta", equalTo(true))
                .body("mensagem", equalTo("Responda agora."));
    }

    @Test
    void deveDistinguirEstadosNaoDisponiveis() {
        for (EstadoCampanha estado : new EstadoCampanha[]{
                EstadoCampanha.RASCUNHO, EstadoCampanha.APROVADA, EstadoCampanha.ENCERRADA}) {
            String codigo = criarCampanha(estado, "Mensagem " + estado);

            given()
                    .when().get("/campanhas/{codigo}/disponibilidade", codigo)
                    .then()
                    .statusCode(200)
                    .body("estado", equalTo(estado.name()))
                    .body("disponivelParaResposta", equalTo(false))
                    .body("mensagem", equalTo("Mensagem " + estado));
        }
    }

    @Test
    void deveRetornar404ParaCampanhaInexistente() {
        given()
                .when().get("/campanhas/campanha-que-nao-existe/disponibilidade")
                .then()
                .statusCode(404);
    }

    @Transactional
    String criarCampanha(EstadoCampanha estado, String mensagem) {
        Campanha campanha = new Campanha();
        campanha.setCodigo("status-" + estado.name().toLowerCase() + "-" + System.nanoTime());
        campanha.setNome("Campanha de teste");
        campanha.setEstado(estado);
        campanha.setDataInicio(java.time.LocalDate.now(org.femass.service.DisponibilidadeCampanhaService.ZONE));
        campanha.setDataFim(campanha.getDataInicio());
        campanha.setMensagemDisponibilidade(mensagem);
        campanhaRepository.persist(campanha);
        return campanha.getCodigo();
    }
}
