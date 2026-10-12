package org.femass;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.femass.service.EmailSender;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class VerificacaoEmailResourceTest {
    @jakarta.inject.Inject jakarta.persistence.EntityManager entityManager;
    @jakarta.inject.Inject CampaignTestFixtures campaignFixtures;
    @org.junit.jupiter.api.BeforeEach void scheduleFixture() {
        campaignFixtures.scheduleSeed();
        org.mockito.Mockito.when(emailSender.configurado()).thenReturn(true);
    }
    @org.junit.jupiter.api.AfterEach void clearFixture() { campaignFixtures.clearSeed(); }
    @InjectMock
    EmailSender emailSender;

    @Test
    void permiteReenviosETentativasSemLimiteEInvalidaSomentePinAnterior() {
        String email = "sem-limite-" + UUID.randomUUID().toString().replaceAll("[0-9]", "a") + "@femass.edu.br";
        String request = """
                {"email":"%s","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}
                """.formatted(email);
        java.util.List<Number> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) ids.add(given().contentType("application/json").body(request)
                .post("/verificacao-email/solicitar").then().statusCode(202).extract().path("verificationId"));
        ArgumentCaptor<String> pins = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(8)).enviarPin(anyString(), pins.capture());
        given().contentType("application/json").body(java.util.Map.of("verificationId", ids.get(0), "pin", pins.getAllValues().get(0)))
                .post("/verificacao-email/confirmar").then().statusCode(400);
        Number lastId = ids.get(7);
        // Um contador persistido acima do antigo limite também não bloqueia a confirmação.
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
                "update VerificacaoEmail set tentativas = 1000 where id = :id").setParameter("id", lastId.longValue()).executeUpdate());
        for (int i = 0; i < 8; i++) given().contentType("application/json").body(java.util.Map.of("verificationId", lastId, "pin", "INCORRETO"))
                .post("/verificacao-email/confirmar").then().statusCode(400);
        given().contentType("application/json").body(java.util.Map.of("verificationId", lastId, "pin", pins.getAllValues().get(7)))
                .post("/verificacao-email/confirmar").then().statusCode(200).body("submissionToken", notNullValue());
    }

    @Test
    void codigoExpiradoContinuaBloqueado() {
        String email = "expirado-" + UUID.randomUUID().toString().replaceAll("[0-9]", "a") + "@femass.edu.br";
        Number id = given().contentType("application/json").body(java.util.Map.of("email", email, "publico", "funcionario",
                "campaign", "cpa-2026", "form", "funcionario_gestao", "formVersion", 1))
                .post("/verificacao-email/solicitar").then().statusCode(202).extract().path("verificationId");
        ArgumentCaptor<String> pin = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviarPin(anyString(), pin.capture());
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
                "update VerificacaoEmail set expiraEm = :expiry where id = :id")
                .setParameter("expiry", java.time.LocalDateTime.now().minusMinutes(1)).setParameter("id", id.longValue()).executeUpdate());
        given().contentType("application/json").body(java.util.Map.of("verificationId", id, "pin", pin.getValue()))
                .post("/verificacao-email/confirmar").then().statusCode(400);
    }

    @Test
    void falhaDeServicoNaoRetornaRateLimit() {
        org.mockito.Mockito.doThrow(new IllegalStateException("Falha no serviço de envio"))
                .when(emailSender).enviarPin(anyString(), anyString());
        given().contentType("application/json").body(java.util.Map.of("email", "professor@femass.edu.br", "publico", "professor",
                "campaign", "cpa-2026", "form", "docente_gestao", "formVersion", 1))
                .post("/verificacao-email/solicitar").then().statusCode(503);
    }

    @Test
    void deveAutorizarTodosOsFormulariosDaJornadaDoProfessor() {
        String email = "professor-" + UUID.randomUUID().toString().replaceAll("[0-9]", "a") + "@femass.edu.br";
        Number verificationId = given().contentType("application/json").body("""
                {"email":"%s","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}
                """.formatted(email)).when().post("/verificacao-email/solicitar")
                .then().statusCode(202).body("verificationId", notNullValue())
                .extract().path("verificationId");

        ArgumentCaptor<String> pinCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviarPin(anyString(), pinCaptor.capture());
        String pin = pinCaptor.getValue();
        assertEquals(16, pin.length());

        String token = given().contentType("application/json").body("""
                {"verificationId":%d,"pin":"%s"}
                """.formatted(verificationId.longValue(), pin)).when().post("/verificacao-email/confirmar")
                .then().statusCode(200).body("submissionToken", notNullValue())
                .extract().path("submissionToken");

        given().contentType("application/json").body("""
                {"campaign":"cpa-2026", "form":"docente_instituicao", "formVersion":1,
                 "respondent":{"type":"professor","emailVerificationToken":"%s","aceiteTermosCondicoesServico":true},
                 "answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}
                """.formatted(token)).when().post("/formulario").then().statusCode(200);

        given().contentType("application/json").body("""
                {"campaign":"cpa-2026", "form":"docente_gestao", "formVersion":1,
                 "respondent":{"type":"professor","emailVerificationToken":"%s","aceiteTermosCondicoesServico":true},
                 "answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}
                """.formatted(token)).when().post("/formulario").then().statusCode(200);

        given().contentType("application/json").body("""
                {"campaign":"cpa-2026", "form":"funcionario_gestao", "formVersion":1,
                 "respondent":{"type":"funcionario","emailVerificationToken":"%s","aceiteTermosCondicoesServico":true},
                 "answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}
                """.formatted(token)).when().post("/formulario").then().statusCode(400);
    }

    @Test
    void deveRejeitarEmailExterno() {
        given().contentType("application/json").body("""
                {"email":"pessoa@gmail.com","publico":"funcionario","campaign":"cpa-2026","form":"funcionario_gestao","formVersion":1}
                """).when().post("/verificacao-email/solicitar").then().statusCode(400);
    }

    @Test
    void deveRejeitarMatriculaComoEmailInstitucionalSemEnviarPin() {
        given().contentType("application/json").body("""
                {"email":"2301130025@femass.edu.br","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}
                """).when().post("/verificacao-email/solicitar").then().statusCode(400);

        verify(emailSender, never()).enviarPin(anyString(), anyString());
    }

    @Test
    void reenvioInvalidaPinAnterior() {
        String email = "reenvio-" + UUID.randomUUID().toString().replaceAll("[0-9]", "a") + "@femass.edu.br";
        String request = """
                {"email":"%s","publico":"funcionario","campaign":"cpa-2026","form":"funcionario_gestao","formVersion":1}
                """.formatted(email);
        Number primeiro = given().contentType("application/json").body(request).when().post("/verificacao-email/solicitar")
                .then().statusCode(202).extract().path("verificationId");
        Number segundo = given().contentType("application/json").body(request).when().post("/verificacao-email/solicitar")
                .then().statusCode(202).extract().path("verificationId");
        ArgumentCaptor<String> pinCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).enviarPin(anyString(), pinCaptor.capture());
        String pinAnterior = pinCaptor.getAllValues().get(0);

        given().contentType("application/json").body("""
                {"verificationId":%d,"pin":"%s"}
                """.formatted(primeiro.longValue(), pinAnterior)).when().post("/verificacao-email/confirmar").then().statusCode(400);
        assertEquals(false, primeiro.longValue() == segundo.longValue());
    }
}
