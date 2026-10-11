package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class AdminCampanhaResourceTest {
    @Test
    void devePermitirAdministracaoComPapelDaComissao() {
        String codigo = "teste-admin-" + System.nanoTime();
        given().contentType("application/json").body("""
                {"codigo":"%s","nome":"Campanha de teste","dataInicio":"2026-10-01","dataFim":"2026-10-31"}
                """.formatted(codigo)).when().post("/admin/campanhas")
                .then().statusCode(201).body("estado", equalTo("RASCUNHO"));
    }
}
