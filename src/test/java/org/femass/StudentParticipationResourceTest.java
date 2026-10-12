package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.entity.*;
import org.femass.service.DisponibilidadeCampanhaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StudentParticipationResourceTest {
    @Inject EntityManager em;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    private String campaign;
    private String otherCampaign;
    private String course;
    private final List<Long> disciplines = new ArrayList<>();
    @BeforeEach void setup() {
        campaign = "aluno-" + UUID.randomUUID(); otherCampaign = "aluno-" + UUID.randomUUID();
        course = "Curso " + UUID.randomUUID(); disciplines.clear();
        QuarkusTransaction.requiringNew().run(() -> {
            var c = new Curso(); c.setNome(course); em.persist(c);
            for (int i = 0; i < 2; i++) {
                var d = new Disciplina(); d.setCurso(c); d.setNome("Disciplina " + i); d.setProfessor("Professor " + i);
                em.persist(d); disciplines.add(d.getId());
            }
            for (var code : List.of(campaign, otherCampaign)) {
                var campanha = new Campanha(); campanha.setCodigo(code); campanha.setNome("Campanha identificada");
                campanha.setAtiva(true); campanha.setEstado(EstadoCampanha.ABERTA);
                campanha.setDataInicio(disponibilidade.hoje()); campanha.setDataFim(disponibilidade.hoje()); em.persist(campanha);
                for (var form : List.of("geral", "outro", "disciplinas")) {
                    var f = new Formulario(); f.setCodigo(form); f.setNome(form); f.setPublico(PublicoAvaliacao.ALUNO); f.setCampanha(campanha); em.persist(f);
                    for (int n = 1; n <= 2; n++) {
                        var v = new FormularioVersao(); v.setFormulario(f); v.setNumero(n); v.setNome(form); v.setPublico(PublicoAvaliacao.ALUNO);
                        v.setEstado(EstadoFormularioVersao.PUBLICADA); v.setEscopo(form.equals("disciplinas") ? EscopoFormulario.DISCIPLINA : EscopoFormulario.GERAL);
                        v.setOrdem(n); em.persist(v);
                        var p = new PerguntaVersao(); p.setFormularioVersao(v); p.setCodigo("q1"); p.setTexto("Pergunta"); p.setOrdem(1); em.persist(p);
                        var o = new OpcaoPergunta(); o.setPergunta(p); o.setCodigo("sim"); o.setRotulo("Sim"); o.setValor(1); o.setOrdem(1); em.persist(o);
                    }
                }
            }
        });
    }
    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> em.createQuery("update Campanha set estado = :estado where codigo in :codes")
            .setParameter("estado", EstadoCampanha.ENCERRADA).setParameter("codes", List.of(campaign, otherCampaign)).executeUpdate());
    }
    private Map<String,Object> payload(String campanha, String form, int versao, String cpf, String matricula) {
        var payload = new HashMap<String,Object>();
        payload.put("campaign", campanha); payload.put("form", form); payload.put("formVersion", versao);
        payload.put("respondent", Map.of("type", "aluno", "cpf", cpf, "matricula", matricula, "aceiteTermosCondicoesServico", true));
        if (form.equals("disciplinas")) {
            payload.put("course", Map.of("name", course));
            payload.put("subjects", disciplines.stream().map(id -> Map.of("subjectId", id.toString(), "answers", answers())).toList());
        } else payload.put("answers", answers());
        return payload;
    }
    private List<Map<String,String>> answers() { return List.of(Map.of("questionId", "q1", "optionCode", "sim")); }
    private Map<String,Object> payload(String form, int versao) { return payload(campaign, form, versao, "52998224725", "0001234567"); }
    private String submit(Map<String,Object> dto) {
        return given().contentType("application/json").body(dto).post("/formulario").then().statusCode(200).extract().path("codigoValidacao");
    }
    private long count(String entity) { return em.createQuery("select count(e) from " + entity + " e where e.formularioVersao.formulario.campanha.codigo = :codigo", Long.class).setParameter("codigo", campaign).getSingleResult(); }
    @Test void bloqueiaCpfOuMatriculaMasPermiteNovaVersaoFormularioECampanha() {
        submit(payload("geral", 1));
        given().contentType("application/json").body(payload("geral", 1)).post("/formulario")
            .then().statusCode(409).body("code", is("FORMULARIO_JA_RESPONDIDO"));
        for (var identity : List.of(List.of("529.982.247-25", "9991234567"), List.of("11144477735", "0001234567")))
            given().contentType("application/json").body(payload(campaign, "geral", 1, identity.get(0), identity.get(1))).post("/formulario")
                .then().statusCode(409).body("code", is("FORMULARIO_JA_RESPONDIDO"));
        submit(payload("geral", 2)); submit(payload("outro", 1));
        submit(payload(otherCampaign, "geral", 1, "52998224725", "0001234567"));
        submit(payload(campaign, "geral", 1, "11144477735", "9991234567"));
        assertEquals(4, count("Validacao")); assertEquals(4, count("Avaliacao"));
        var hashes = em.createQuery("select v.cpfHash from Validacao v where v.formularioVersao.formulario.campanha.codigo = :codigo", String.class).setParameter("codigo", campaign).getResultList();
        assertTrue(hashes.stream().allMatch(hash -> hash.matches("[a-f0-9]{64}")));
    }
    @Test void trocarDisciplinaOuExpirarComprovanteNaoLiberaNovaParticipacao() {
        var first = payload("disciplinas", 1);
        first.put("subjects", List.of(Map.of("subjectId", disciplines.get(0).toString(), "answers", answers())));
        String receipt = submit(first);
        given().queryParam("codigoValidacao", receipt).put("/validacao/validar-hash").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("update Validacao v set v.expiraEm = :expired where v.formularioVersao.formulario.campanha.codigo = :code")
                .setParameter("expired", java.time.LocalDateTime.now().minusDays(1)).setParameter("code", campaign).executeUpdate();
            em.createQuery("update Formulario f set f.ativo = false where f.campanha.codigo = :code").setParameter("code", campaign).executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() -> em.createQuery("update Formulario f set f.ativo = true where f.campanha.codigo = :code")
            .setParameter("code", campaign).executeUpdate());
        var differentSubject = payload("disciplinas", 1);
        differentSubject.put("subjects", List.of(Map.of("subjectId", disciplines.get(1).toString(), "answers", answers())));
        for (var repeated : List.of(first, differentSubject, payload("disciplinas", 1)))
            given().contentType("application/json").body(repeated).post("/formulario")
                .then().statusCode(409).body("code", is("FORMULARIO_JA_RESPONDIDO")).body("codigoValidacao", nullValue());
        assertEquals(1, count("Validacao")); assertEquals(1, count("Avaliacao"));
        assertEquals(1L, em.createQuery("select count(r) from Resposta r where r.avaliacao.formularioVersao.formulario.campanha.codigo = :code", Long.class)
            .setParameter("code", campaign).getSingleResult());
        submit(payload("disciplinas", 2));
        assertEquals(2, count("Validacao")); assertEquals(3, count("Avaliacao"));
    }
    @Test void vinculaTodasDisciplinasELeituraHistoricaNaoExpoeIdentidadeOuConteudo() {
        String token = submit(payload("disciplinas", 1));
        var response = given().queryParam("codigoValidacao", token).put("/validacao/validar-hash").then().statusCode(200)
            .body("resumo.campanha.codigo", is(campaign)).body("resumo.formulario.versao", is(1))
            .body("resumo.avaliacoes.size()", is(2)).body("resumo.avaliacoes.disciplina.id", hasItems(disciplines.get(0).intValue(), disciplines.get(1).intValue()))
            .body("resumo.avaliacoes[0].respostaIds.size()", is(1)).extract();
        long validationId = response.jsonPath().getLong("resumo.validacaoId");
        String json = response.asString();
        for (String value : List.of("52998224725", "0001234567", "cpf", "matricula", "nota", "comentario", "opcao")) assertFalse(json.contains(value));
        assertEquals(2L, em.createQuery("select count(a) from Avaliacao a where a.validacao.id = :id", Long.class).setParameter("id", validationId).getSingleResult());
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("update Campanha set ativa = false where codigo = :c").setParameter("c", campaign).executeUpdate();
            em.createQuery("update Disciplina set ativo = false where id in :ids").setParameter("ids", disciplines).executeUpdate();
        });
        given().get("/validacao/historico").then().statusCode(200).body("find { it.resumo.validacaoId == " + validationId + " }.resumo.avaliacoes.size()", is(2));
        given().queryParam("codigoValidacao", token).put("/validacao/validar-hash").then().statusCode(409);
    }
    @Test void rejeitaInvalidoSemGravarEPermiteCorrigir() {
        for (var identity : List.of(List.of("", "0001234567"), List.of("52998224725", ""),
                List.of("11111111111", "0001234567"), List.of("52998224725", "123"))) {
            given().contentType("application/json").body(payload(campaign, "geral", 1, identity.get(0), identity.get(1)))
                .post("/formulario").then().statusCode(400);
        }
        var invalid = payload("disciplinas", 1);
        invalid.put("subjects", List.of(Map.of("subjectId", disciplines.get(0).toString(), "answers", answers()), Map.of("subjectId", "999999999", "answers", answers())));
        given().contentType("application/json").body(invalid).post("/formulario").then().statusCode(400);
        invalid.put("subjects", List.of(Map.of("subjectId", disciplines.get(0).toString(), "answers", answers()), Map.of("subjectId", disciplines.get(0).toString(), "answers", answers())));
        given().contentType("application/json").body(invalid).post("/formulario").then().statusCode(400);
        assertEquals(0, count("Validacao")); assertEquals(0, count("Avaliacao"));
        submit(payload("disciplinas", 1));
    }
    @ParameterizedTest
    @ValueSource(strings = {"geral", "disciplinas"})
    void concorrenciaGravaUmaUnicaParticipacao(String form) throws Exception {
        var start = new CountDownLatch(1); var ready = new CountDownLatch(2); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> { ready.countDown(); start.await(); return given().contentType("application/json").body(payload(form, 1)).post("/formulario").statusCode(); }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var statuses = new ArrayList<Integer>(); for (var f : futures) statuses.add(f.get(20, TimeUnit.SECONDS)); Collections.sort(statuses);
            assertEquals(List.of(200,409), statuses); assertEquals(1, count("Validacao"));
            assertEquals(form.equals("disciplinas") ? 2 : 1, count("Avaliacao"));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    @Test void geralEDetalhadoPossuemContextoSemDisciplina() {
        String token = submit(payload("geral", 1));
        given().queryParam("codigoValidacao", token).put("/validacao/validar-hash/detalhado").then().statusCode(200)
            .body("resumo.formulario.escopo", is("GERAL")).body("resumo.avaliacoes[0].disciplina", nullValue()).body("resumo.avaliacoes[0].curso", nullValue());
    }
}
