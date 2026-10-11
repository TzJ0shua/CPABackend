package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.entity.*;
import org.femass.repository.CampanhaRepository;
import org.femass.service.EmailSender;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class CampaignPeriodResourceTest {
    @InjectMock Clock clock;
    @InjectMock EmailSender emailSender;
    @Inject CampanhaRepository repository;
    @Inject EntityManager em;
    private final LocalDate today = LocalDate.of(2036, 10, 10);

    @BeforeEach void setup() {
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2036-10-10T15:00:00Z"), ZoneOffset.UTC)
            .withZone(ZoneId.of("America/Sao_Paulo")));
        reset(emailSender);
    }
    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            repository.update("estado = ?1 where codigo like ?2", EstadoCampanha.ENCERRADA, "period-%");
            var c = repository.findByCodigo("cpa-2026"); c.setDataInicio(null); c.setDataFim(null);
        });
    }
    private String create(LocalDate start, LocalDate end, boolean active) {
        String code = "period-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            Campanha c = new Campanha(); c.setCodigo(code); c.setNome(code); c.setEstado(EstadoCampanha.APROVADA);
            c.setAtiva(active); c.setDataInicio(start); c.setDataFim(end); repository.persist(c);
        });
        return "/admin/campanhas/" + code;
    }
    private Map<String,Object> dto(LocalDate start, LocalDate end) {
        return Map.of("nome", "Campanha", "dataInicio", start.toString(), "dataFim", end.toString());
    }
    @Test void validaDatasESelecionaSomenteCampanhaVigente() {
        given().get("/campanhas/atual/disponibilidade").then().statusCode(200)
            .body("campanha", nullValue()).body("estado", nullValue()).body("dataInicio", nullValue())
            .body("disponivelParaResposta", is(false));
        given().contentType("application/json").body(Map.of("codigo", "period-" + UUID.randomUUID(), "nome", "Campanha"))
            .post("/admin/campanhas").then().statusCode(400);
        var invalid = new HashMap<>(dto(today.plusDays(1), today)); invalid.put("codigo", "period-" + UUID.randomUUID());
        given().contentType("application/json").body(invalid).post("/admin/campanhas").then().statusCode(400);
        invalid.put("dataInicio", "invalid");
        given().contentType("application/json").body(invalid).post("/admin/campanhas").then().statusCode(400);
        String unscheduled = create(null, null, true);
        given().post(unscheduled + "/abrir").then().statusCode(400);
        String expired = create(today.minusDays(3), today.minusDays(1), true);
        given().post(expired + "/abrir").then().statusCode(204);
        given().get(expired.replace("/admin", "") + "/disponibilidade").then().body("disponivelParaResposta", is(false));
        String future = create(today.plusDays(1), today.plusDays(3), true);
        given().post(future + "/abrir").then().statusCode(204);
        given().get(future.replace("/admin", "") + "/disponibilidade").then().body("disponivelParaResposta", is(false));
        String current = create(today, today, true);
        given().post(current + "/abrir").then().statusCode(204);
        given().get("/campanhas/atual/disponibilidade").then().statusCode(200)
            .body("campanha", is(current.substring(current.lastIndexOf('/') + 1)))
            .body("dataInicio", is(today.toString())).body("dataFim", is(today.toString())).body("disponivelParaResposta", is(true));
        given().contentType("application/json").body(Map.of("ativo", false)).patch(current + "/status").then().statusCode(204);
        given().get("/campanhas/atual/disponibilidade").then().body("disponivelParaResposta", is(false));
    }
    @Test void impedeSobreposicaoNaAberturaReativacaoEEdicao() {
        String a = create(today, today.plusDays(2), true);
        String b = create(today.plusDays(2), today.plusDays(4), true);
        given().post(a + "/abrir").then().statusCode(204);
        given().post(b + "/abrir").then().statusCode(409).body("message", containsString(a.substring(a.lastIndexOf('/') + 1)));
        given().contentType("application/json").body(Map.of("ativo", false)).patch(a + "/status").then().statusCode(204);
        given().post(b + "/abrir").then().statusCode(204);
        given().contentType("application/json").body(Map.of("ativo", true)).patch(a + "/status").then().statusCode(409);
        given().get(a).then().body("ativo", is(false));
        given().contentType("application/json").body(dto(today.plusDays(3), today.plusDays(4))).put(b).then().statusCode(204);
        given().contentType("application/json").body(Map.of("ativo", true)).patch(a + "/status").then().statusCode(204);
        given().contentType("application/json").body(dto(today.plusDays(2), today.plusDays(4))).put(b).then().statusCode(409);
        given().get(b).then().body("dataInicio", is(today.plusDays(3).toString()));
    }
    @Test void serializaAberturasConcorrentes() throws Exception {
        String a = create(today.plusDays(10), today.plusDays(20), true);
        String b = create(today.plusDays(15), today.plusDays(30), true);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (String path : List.of(a,b)) futures.add(executor.submit(() -> {
                ready.countDown(); start.await(); return given().post(path + "/abrir").statusCode();
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var codes = new ArrayList<Integer>(); for (var f : futures) codes.add(f.get(20, TimeUnit.SECONDS));
            Collections.sort(codes); assertEquals(List.of(204, 409), codes);
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    @Test void bloqueiaCatalogoEEnvioExpiradoSemConsumirAutorizacao() {
        QuarkusTransaction.requiringNew().run(() -> {
            var c = repository.findByCodigo("cpa-2026"); c.setAtiva(true); c.setEstado(EstadoCampanha.ABERTA);
            c.setDataInicio(today); c.setDataFim(today);
        });
        long id = given().contentType("application/json").body("""
            {"email":"periodo@femass.edu.br","publico":"professor","campaign":"cpa-2026","form":"docente_gestao","formVersion":1}
            """).post("/verificacao-email/solicitar").then().statusCode(202).extract().jsonPath().getLong("verificationId");
        var pin = ArgumentCaptor.forClass(String.class); verify(emailSender).enviarPin(anyString(), pin.capture());
        String token = given().contentType("application/json").body(Map.of("verificationId", id, "pin", pin.getValue()))
            .post("/verificacao-email/confirmar").then().statusCode(200).extract().path("submissionToken");
        long answers = em.createQuery("select count(r) from Resposta r", Long.class).getSingleResult();
        QuarkusTransaction.requiringNew().run(() -> {
            var c = repository.findByCodigo("cpa-2026"); c.setDataInicio(today.minusDays(2)); c.setDataFim(today.minusDays(1));
        });
        given().get("/formularios?campaign=cpa-2026&publico=professor").then().statusCode(200).body("size()", is(0));
        given().get("/perguntas?campaign=cpa-2026&publico=professor&form=docente_gestao&version=1").then().statusCode(400);
        given().contentType("application/json").body(Map.of("campaign", "cpa-2026", "form", "docente_gestao", "formVersion", 1,
            "respondent", Map.of("type", "professor", "aceiteTermosCondicoesServico", true, "emailVerificationToken", token), "answers", List.of()))
            .post("/formulario").then().statusCode(409).contentType("application/json");
        assertEquals(answers, em.createQuery("select count(r) from Resposta r", Long.class).getSingleResult());
        var status = em.createQuery("select v.status from VerificacaoEmail v where v.id = :id", StatusVerificacaoEmail.class)
            .setParameter("id", id).getSingleResult();
        assertEquals(StatusVerificacaoEmail.VERIFICADO, status);
    }
}
