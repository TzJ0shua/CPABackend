package org.femass;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.femass.service.EmailSender;
import org.femass.repository.VerificacaoEmailRepository;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestProfile(ChaveMestraResourceTest.Profile.class)
class ChaveMestraResourceTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.of("verificacao-email.chave-mestra", "chave-exclusiva-dos-testes");
        }
    }
    @Inject CampaignTestFixtures fixtures;
    @Inject VerificacaoEmailRepository repository;
    @InjectMock EmailSender sender;
    @BeforeEach void setup() { fixtures.scheduleSeed(); }
    @AfterEach void cleanup() { fixtures.clearSeed(); }

    private String request(String publico, String form, String chave) {
        return """
            {"chave":"%s","publico":"%s","campaign":"cpa-2026","form":"%s","formVersion":1}
            """.formatted(chave, publico, form);
    }
    private String authorize(String publico, String form) {
        return given().contentType("application/json").body(request(publico, form, "chave-exclusiva-dos-testes"))
            .post("/verificacao-email/chave-mestra").then().statusCode(200)
            .header("Cache-Control", "no-store").body("submissionToken", notNullValue())
            .extract().path("submissionToken");
    }
    private void submit(String publico, String form, String token, int status) {
        given().contentType("application/json").body("""
            {"campaign":"cpa-2026","form":"%s","formVersion":1,
             "respondent":{"type":"%s","emailVerificationToken":"%s","aceiteTermosCondicoesServico":true},
             "answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}
            """.formatted(form, publico, token)).post("/formulario").then().statusCode(status);
    }
    @Test void configuracaoSemEmailNaoExpoeSegredosEEmailDiretoRetorna503() {
        given().get("/verificacao-email/configuracao").then().statusCode(200)
            .header("Cache-Control", "no-store").body("emailDisponivel", is(false))
            .body("chaveMestraDisponivel", is(true)).body("size()", is(2));
        given().contentType("application/json").body("""
            {"email":"professor@femass.edu.br","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}
            """).post("/verificacao-email/solicitar").then().statusCode(503);
        when(sender.configurado()).thenReturn(true);
        given().get("/verificacao-email/configuracao").then().body("emailDisponivel", is(true));
        verify(sender, never()).enviarPin(anyString(), anyString());
    }
    @Test void chaveAutorizaJornadaSemEmailEImpedeTrocaDePublico() {
        String token = authorize("professor", "docente_gestao");
        submit("professor", "docente_gestao", token, 200);
        submit("professor", "docente_gestao", token, 200); // Chave mestra permanece como exceção.
        submit("professor", "docente_instituicao", token, 200);
        submit("funcionario", "funcionario_gestao", token, 400);
        String funcionario = authorize("funcionario", "funcionario_gestao");
        submit("funcionario", "funcionario_gestao", funcionario, 200);
        submit("funcionario", "funcionario_gestao", funcionario, 200); // Mesma exceção para funcionário.
        submit("professor", "docente_gestao", "chave-exclusiva-dos-testes", 400);
        verify(sender, never()).enviarPin(anyString(), anyString());
    }
    @Test void rejeitaChaveInvalidaPublicoIncorretoFormularioAusenteEPeriodoIndisponivel() {
        given().contentType("application/json").body(request("professor", "docente_gestao", "errada"))
            .post("/verificacao-email/chave-mestra").then().statusCode(401);
        given().contentType("application/json").body(request("aluno", "discente_gestao", "chave-exclusiva-dos-testes"))
            .post("/verificacao-email/chave-mestra").then().statusCode(400);
        given().contentType("application/json").body(request("professor", "ausente", "chave-exclusiva-dos-testes"))
            .post("/verificacao-email/chave-mestra").then().statusCode(400);
        fixtures.clearSeed();
        given().contentType("application/json").body(request("professor", "docente_gestao", "chave-exclusiva-dos-testes"))
            .post("/verificacao-email/chave-mestra").then().statusCode(409);
    }
    @Test void autorizacaoExpiraENaoUltrapassaDisponibilidadeDaCampanha() {
        String token = authorize("professor", "docente_gestao");
        QuarkusTransaction.requiringNew().run(() -> repository.update("autorizacaoExpiraEm = ?1 where status = ?2",
            LocalDateTime.now().minusMinutes(1), org.femass.entity.StatusVerificacaoEmail.VERIFICADO));
        submit("professor", "docente_gestao", token, 400);
        String fresh = authorize("professor", "docente_gestao");
        fixtures.clearSeed();
        submit("professor", "docente_gestao", fresh, 409);
    }
}
