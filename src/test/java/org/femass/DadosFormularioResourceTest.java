package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DadosFormularioResourceTest {
    @jakarta.inject.Inject CampaignTestFixtures campaignFixtures;
    @org.junit.jupiter.api.BeforeEach void scheduleFixture() { campaignFixtures.scheduleSeed(); }
    @org.junit.jupiter.api.AfterEach void clearFixture() { campaignFixtures.clearSeed(); }
    @Test
    void deveExigirContextoDoCatalogo() {
        given().when().get("/dados-formulario").then().statusCode(400);
        given().when().get("/perguntas").then().statusCode(400);
        given().when().get("/formularios").then().statusCode(400);
    }

    @Test
    void deveBuscarCatalogoVersionado() {
        given().queryParam("campaign", "cpa-2026").queryParam("publico", "aluno")
                .when().get("/dados-formulario")
                .then().statusCode(200)
                .body("cursos", notNullValue())
                .body("formularios.size()", equalTo(4))
                .body("formularios.find { it.code == 'discente_disciplinas' }.scope", equalTo("DISCIPLINA"));

        given().queryParam("campaign", "cpa-2026").queryParam("publico", "aluno")
                .queryParam("form", "discente_disciplinas").queryParam("version", 1)
                .when().get("/perguntas")
                .then().statusCode(200)
                .body("size()", equalTo(10))
                .body("[0].options.find { it.naoSeiResponder }.code", equalTo("nao_sei_responder"));
    }
}
