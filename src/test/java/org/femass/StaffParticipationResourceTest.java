package org.femass;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.entity.*;
import org.femass.service.DisponibilidadeCampanhaService;
import org.femass.service.EmailSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class StaffParticipationResourceTest {
    @Inject EntityManager em;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    @InjectMock EmailSender sender;
    private String campaign;
    private String otherCampaign;
    private String course;
    private Long discipline;
    private Long otherDiscipline;
    private final Map<String, String> pins = new ConcurrentHashMap<>();

    @BeforeEach void setup() {
        campaign = "email-" + UUID.randomUUID(); otherCampaign = "email-" + UUID.randomUUID();
        course = "Curso email " + UUID.randomUUID(); pins.clear();
        when(sender.configurado()).thenReturn(true);
        doAnswer(invocation -> { pins.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(sender).enviarPin(anyString(), anyString());
        QuarkusTransaction.requiringNew().run(() -> {
            var c = new Curso(); c.setNome(course); em.persist(c);
            var d = new Disciplina(); d.setCurso(c); d.setNome("Disciplina email"); d.setProfessor("Professor"); em.persist(d); discipline = d.getId();
            var other = new Disciplina(); other.setCurso(c); other.setNome("Outra disciplina email"); other.setProfessor("Outro professor");
            em.persist(other); otherDiscipline = other.getId();
            for (var code : List.of(campaign, otherCampaign)) {
                var campanha = new Campanha(); campanha.setCodigo(code); campanha.setNome("Campanha email");
                campanha.setAtiva(true); campanha.setEstado(EstadoCampanha.ABERTA);
                campanha.setDataInicio(disponibilidade.hoje()); campanha.setDataFim(disponibilidade.hoje()); em.persist(campanha);
                for (var publico : List.of(PublicoAvaliacao.PROFESSOR, PublicoAvaliacao.FUNCIONARIO)) {
                    for (var form : List.of("geral", "outro", "disciplinas")) {
                        var f = new Formulario(); f.setCodigo(form + "_" + publico.getValor()); f.setNome(form); f.setPublico(publico); f.setCampanha(campanha); em.persist(f);
                        for (int n = 1; n <= 2; n++) {
                            var v = new FormularioVersao(); v.setFormulario(f); v.setNumero(n); v.setNome(form); v.setPublico(publico);
                            v.setEstado(EstadoFormularioVersao.PUBLICADA); v.setEscopo(form.equals("disciplinas") ? EscopoFormulario.DISCIPLINA : EscopoFormulario.GERAL);
                            v.setOrdem(n); v.setComentarioPermitido(n == 1); v.setAvisoComentario(n == 1 ? "Comentário opcional." : null); em.persist(v);
                            var p = new PerguntaVersao(); p.setFormularioVersao(v); p.setCodigo("q1"); p.setTexto("Pergunta"); p.setOrdem(1); em.persist(p);
                            var o = new OpcaoPergunta(); o.setPergunta(p); o.setCodigo("sim"); o.setRotulo("Sim"); o.setValor(1); o.setOrdem(1); em.persist(o);
                        }
                    }
                }
            }
        });
    }
    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> em.createQuery("update Campanha set estado = :estado where codigo in :codes")
                .setParameter("estado", EstadoCampanha.ENCERRADA).setParameter("codes", List.of(campaign, otherCampaign)).executeUpdate());
    }
    private String email() { return "pessoa-" + UUID.randomUUID().toString().replaceAll("[0-9]", "a") + "@femass.edu.br"; }
    private String authorize(String email, String publico, String code) {
        Number id = given().contentType("application/json").body(Map.of("email", email, "publico", publico,
                "campaign", code, "form", "geral_" + publico, "formVersion", 1)).post("/verificacao-email/solicitar")
                .then().statusCode(202).extract().path("verificationId");
        return given().contentType("application/json").body(Map.of("verificationId", id, "pin", pins.get(email.trim().toLowerCase(Locale.ROOT))))
                .post("/verificacao-email/confirmar").then().statusCode(200).extract().path("submissionToken");
    }
    private Map<String, Object> payload(String code, String publico, String form, int version, String token) {
        var payload = new HashMap<String, Object>();
        payload.put("campaign", code); payload.put("form", form + "_" + publico); payload.put("formVersion", version);
        payload.put("respondent", Map.of("type", publico, "emailVerificationToken", token, "aceiteTermosCondicoesServico", true,
                "email", "email-trocado@femass.edu.br")); // O envio não pode substituir a identidade confirmada.
        var answers = List.of(Map.of("questionId", "q1", "optionCode", "sim"));
        if (form.equals("disciplinas")) {
            payload.put("course", Map.of("name", course));
            payload.put("subjects", List.of(Map.of("subjectId", discipline.toString(), "answers", answers, "comment", "Comentário da disciplina")));
        } else payload.put("answers", answers);
        return payload;
    }
    private String submit(Map<String, Object> payload, int status) {
        var response = given().contentType("application/json").body(payload).post("/formulario").then().statusCode(status);
        if (status == 409) response.body("code", is("FORMULARIO_JA_RESPONDIDO")).body("codigoValidacao", nullValue());
        return status == 200 ? response.extract().path("codigoValidacao") : null;
    }
    private long count(String entity) {
        return em.createQuery("select count(e) from " + entity + " e where e.formularioVersao.formulario.campanha.codigo = :code", Long.class)
                .setParameter("code", campaign).getSingleResult();
    }
    @Test void limitaAmbosPublicosPorEmailConfirmadoEVersaoMesmoComOutraAutorizacao() {
        for (var publico : List.of("professor", "funcionario")) {
            String email = email(); String token = authorize(email, publico, campaign);
            submit(payload(campaign, publico, "geral", 1, token), 200);
            submit(payload(campaign, publico, "geral", 1, token), 409);
            String newToken = authorize(" " + email.toUpperCase(Locale.ROOT) + " ", publico, campaign);
            submit(payload(campaign, publico, "geral", 1, newToken), 409);
            submit(payload(campaign, publico, "geral", 2, newToken), 200);
            submit(payload(campaign, publico, "outro", 1, newToken), 200);
            submit(payload(campaign, publico, "geral", 1, authorize(email(), publico, campaign)), 200);
            submit(payload(otherCampaign, publico, "geral", 1, authorize(email, publico, otherCampaign)), 200);
        }
        assertEquals(8, count("Validacao")); assertEquals(8, count("Avaliacao"));
        var digests = em.createQuery("select v.emailDigest from Validacao v where v.formularioVersao.formulario.campanha.codigo = :code", String.class)
                .setParameter("code", campaign).getResultList();
        assertTrue(digests.stream().allMatch(digest -> digest.matches("[0-9a-f]{64}")));
    }
    @Test void comentariosHabilitadosAparecemNoCatalogoESaoSalvosEmGeralEDisciplina() {
        String token = authorize(email(), "professor", campaign);
        given().queryParam("campaign", campaign).queryParam("publico", "professor").get("/formularios").then().statusCode(200)
                .body("find { it.code == 'geral_professor' && it.version == 1 }.commentAllowed", is(true))
                .body("find { it.code == 'geral_professor' && it.version == 2 }.commentAllowed", is(false));
        var geral = payload(campaign, "professor", "geral", 1, token);
        geral.put("comment", "x".repeat(1001)); submit(geral, 400);
        assertEquals(0, count("Validacao"));
        geral.put("comment", "<b>Comentário geral</b>"); submit(geral, 200);
        submit(payload(campaign, "professor", "disciplinas", 1, token), 200);
        var disabled = payload(campaign, "professor", "geral", 2, token); disabled.put("comment", "Não permitido"); submit(disabled, 400);
        disabled.remove("comment"); submit(disabled, 200);
        var comments = em.createQuery("select a.comentariosGerais from Avaliacao a where a.formularioVersao.formulario.campanha.codigo = :code", String.class)
                .setParameter("code", campaign).getResultList();
        assertTrue(comments.contains("Comentário geral")); assertTrue(comments.contains("Comentário da disciplina"));
        assertEquals(3, count("Validacao"));
    }
    @ParameterizedTest
    @CsvSource({"professor,geral", "funcionario,geral", "professor,disciplinas", "funcionario,disciplinas"})
    void autorizacoesDistintasConcorrentesNaoDuplicamRespostas(String publico, String form) throws Exception {
        String email = email();
        var tokens = List.of(authorize(email, publico, campaign), authorize(email, publico, campaign));
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (var token : tokens) futures.add(executor.submit(() -> {
                ready.countDown(); start.await();
                return given().contentType("application/json").body(payload(campaign, publico, form, 1, token)).post("/formulario").statusCode();
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var statuses = new ArrayList<Integer>(); for (var result : futures) statuses.add(result.get(20, TimeUnit.SECONDS)); Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses); assertEquals(1, count("Validacao")); assertEquals(1, count("Avaliacao"));
            assertEquals(1L, em.createQuery("select count(r) from Resposta r where r.avaliacao.formularioVersao.formulario.campanha.codigo = :code", Long.class)
                .setParameter("code", campaign).getSingleResult());
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    @ParameterizedTest
    @ValueSource(strings = {"professor", "funcionario"})
    void trocarDisciplinaNovoPinOuExpiracaoNaoLiberaRepeticao(String publico) {
        String email = email(), token = authorize(email, publico, campaign);
        String receipt = submit(payload(campaign, publico, "disciplinas", 1, token), 200);
        given().queryParam("codigoValidacao", receipt).put("/validacao/validar-hash").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("update Validacao v set v.expiraEm = :expired where v.formularioVersao.formulario.campanha.codigo = :code")
                .setParameter("expired", java.time.LocalDateTime.now().minusDays(1)).setParameter("code", campaign).executeUpdate();
            em.createQuery("update VerificacaoEmail v set v.autorizacaoExpiraEm = :expired where v.campanha = :code")
                .setParameter("expired", java.time.LocalDateTime.now().minusMinutes(1)).setParameter("code", campaign).executeUpdate();
        });
        submit(payload(campaign, publico, "disciplinas", 1, token), 400);
        String fresh = authorize(" " + email.toUpperCase(Locale.ROOT) + " ", publico, campaign);
        submit(payload(campaign, publico, "disciplinas", 1, fresh), 409);
        var other = payload(campaign, publico, "disciplinas", 1, fresh);
        other.put("subjects", List.of(Map.of("subjectId", otherDiscipline.toString(), "answers", List.of(Map.of("questionId", "q1", "optionCode", "sim")))));
        submit(other, 409);
        assertEquals(1, count("Validacao")); assertEquals(1, count("Avaliacao"));
        other.put("formVersion", 2); submit(other, 200);
        assertEquals(2, count("Validacao")); assertEquals(2, count("Avaliacao"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"professor", "funcionario"})
    void naoPermiteForjarAutorizacaoOuTrocarPublicoECampanha(String publico) {
        String token = authorize(email(), publico, campaign);
        submit(payload(campaign, publico, "geral", 1, ""), 400);
        submit(payload(campaign, publico, "geral", 1, "autorizacao-forjada"), 400);
        submit(payload(otherCampaign, publico, "geral", 1, token), 400);
        submit(payload(campaign, publico.equals("professor") ? "funcionario" : "professor", "geral", 1, token), 400);
        assertEquals(0, count("Validacao")); assertEquals(0, count("Avaliacao"));
        submit(payload(campaign, publico, "geral", 1, token), 200);
        assertEquals(1, count("Validacao"));
    }
}
