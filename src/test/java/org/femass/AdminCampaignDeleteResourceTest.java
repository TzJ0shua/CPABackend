package org.femass;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.dto.AdminViews;
import org.femass.dto.FormularioDTO;
import org.femass.entity.*;
import org.femass.service.DisponibilidadeCampanhaService;
import org.femass.service.FormularioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class AdminCampaignDeleteResourceTest {
    @Inject EntityManager em;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    @Inject FormularioService submissions;
    @Inject ObjectMapper json;
    private final List<String> campaigns = new ArrayList<>();

    private static String base(String code) { return "/admin/campanhas/" + code; }
    private String create(boolean form, boolean open) {
        String code = "delete-" + UUID.randomUUID();
        campaigns.add(code);
        given().contentType("application/json").body(Map.of("codigo", code, "nome", "Campanha para exclusão",
            "dataInicio", disponibilidade.hoje().toString(), "dataFim", disponibilidade.hoje().toString()))
            .post("/admin/campanhas").then().statusCode(201);
        if (form) {
            given().contentType("application/json").body("""
                {"codigo":"geral","nome":"Avaliação","publico":"aluno","escopo":"GERAL","ordem":1,
                 "commentAllowed":true,"commentNotice":"Comentário opcional.","perguntas":[{"codigo":"q1","texto":"Pergunta","ordem":1,"ativo":true,
                 "opcoes":[{"code":"sim","label":"Sim","value":1,"ativo":true}]}]}
                """).post(base(code) + "/formularios").then().statusCode(201);
            given().post(base(code) + "/formularios/geral/versoes/1/publicar").then().statusCode(204);
        }
        if (open) {
            given().post(base(code) + "/aprovar").then().statusCode(204);
            given().post(base(code) + "/abrir").then().statusCode(204);
        }
        return code;
    }
    private AdminViews.ExclusaoCampanha preview(String code) {
        return given().get(base(code) + "/exclusao").then().statusCode(200).extract().as(AdminViews.ExclusaoCampanha.class);
    }
    private static String submission(String code) {
        return """
            {"campaign":"%s","form":"geral","formVersion":1,
             "respondent":{"type":"aluno","aceiteTermosCondicoesServico":true,"cpf":"52998224725","matricula":"0000000001"},
             "answers":[{"questionId":"q1","optionCode":"sim"}],"comment":"Comentário histórico"}
            """.formatted(code);
    }
    private String answer(String code) {
        return given().contentType("application/json").body(submission(code)).post("/formulario")
            .then().statusCode(200).extract().path("codigoValidacao");
    }
    private long count(String table) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table).getSingleResult()).longValue();
    }
    private long authorization(String code) {
        return QuarkusTransaction.requiringNew().call(() -> {
            VerificacaoEmail auth = new VerificacaoEmail(); auth.setCampanha(code); auth.setFormulario("geral");
            auth.setVersaoFormulario(1); auth.setPublico(PublicoAvaliacao.PROFESSOR); auth.setAcessoChaveMestra(true);
            auth.setEmailDigest("a".repeat(64)); auth.setCodigoDigest("b".repeat(64));
            auth.setExpiraEm(LocalDateTime.now().plusHours(1)); em.persist(auth); em.flush(); return auth.getId();
        });
    }
    @AfterEach void closeRemaining() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String code : campaigns)
                em.createQuery("update Campanha c set c.estado = :state where c.codigo = :code")
                    .setParameter("state", EstadoCampanha.ENCERRADA).setParameter("code", code).executeUpdate();
        });
    }

    @Test void excluiCampanhaVaziaEPermiteReutilizarCodigo() {
        String code = create(false, false);
        var summary = preview(code);
        assertEquals(0, summary.respostas()); assertEquals(0, summary.formularios());
        given().contentType("application/json").body(summary).delete(base(code)).then().statusCode(204);
        given().get(base(code)).then().statusCode(404);
        given().get(base(code) + "/exclusao").then().statusCode(404);
        given().contentType("application/json").body(Map.of("codigo", code, "nome", "Recriada",
            "dataInicio", "2038-01-01", "dataFim", "2038-01-01")).post("/admin/campanhas").then().statusCode(201);
    }

    @Test void excluiArvoreSemRespostasIncluindoVersoesInativas() {
        String code = create(true, false);
        given().post(base(code) + "/formularios/geral/versoes").then().statusCode(201);
        given().contentType("application/json").body(Map.of("ativo", false))
            .patch(base(code) + "/formularios/geral/versoes/1/status").then().statusCode(204);
        var summary = preview(code);
        assertEquals(2, summary.versoes()); assertEquals(2, summary.perguntas()); assertEquals(2, summary.opcoes());
        Map<String,Long> before = new HashMap<>();
        for (String table : List.of("formulario", "formulario_versao", "pergunta_versao", "opcao_pergunta")) before.put(table, count(table));
        given().contentType("application/json").body(summary).delete(base(code)).then().statusCode(204);
        assertEquals(before.get("formulario") - 1, count("formulario"));
        for (String table : List.of("formulario_versao", "pergunta_versao", "opcao_pergunta")) assertEquals(before.get(table) - 2, count(table));
        given().queryParam("campaign", code).queryParam("publico", "aluno").get("/formularios").then().statusCode(200).body("size()", is(0));
    }

    @Test void excluiRespostasComprovantesEAutorizacoesPreservandoOutrosDados() {
        String code = create(true, true);
        String receipt = answer(code);
        assertNotNull(receipt);
        long auth = authorization(code);
        String other = create(true, false);
        long otherAuth = authorization(other);
        long legacyProof = QuarkusTransaction.requiringNew().call(() -> {
            Validacao v = new Validacao(); v.setNewHash(UUID.randomUUID()); v.setExpiraEm(LocalDateTime.now().plusHours(1));
            em.persist(v); em.flush(); return v.getId();
        });
        var otherBefore = preview(other);
        var summary = preview(code);
        assertEquals(1, summary.avaliacoes()); assertEquals(1, summary.respostas());
        assertEquals(1, summary.validacoes()); assertEquals(1, summary.autorizacoes());
        long courses = count("curso"), subjects = count("disciplina"), answers = count("resposta"), proofs = count("validacao");
        given().contentType("application/json").body(summary).delete(base(code)).then().statusCode(204);
        given().get(base(code)).then().statusCode(404);
        given().queryParam("codigoValidacao", receipt).get("/validacao/buscar-hash").then().statusCode(404);
        given().contentType("application/json").body(submission(code)).post("/formulario").then().statusCode(409);
        assertEquals(otherBefore, preview(other));
        assertEquals(answers - 1, count("resposta")); assertEquals(proofs - 1, count("validacao"));
        assertEquals(courses, count("curso")); assertEquals(subjects, count("disciplina"));
        QuarkusTransaction.requiringNew().run(() -> {
            em.clear(); assertNull(em.find(VerificacaoEmail.class, auth));
            assertNotNull(em.find(VerificacaoEmail.class, otherAuth)); assertNotNull(em.find(Validacao.class, legacyProof));
        });
    }

    @Test void novaRespostaExigeNovaConfirmacaoSemExclusaoParcial() {
        String code = create(true, true);
        var stale = preview(code); assertEquals(0, stale.respostas());
        String receipt = answer(code);
        given().contentType("application/json").body(stale).delete(base(code)).then().statusCode(409)
            .contentType("application/json").body("message", containsString("confirme novamente"));
        given().get(base(code)).then().statusCode(200);
        given().queryParam("codigoValidacao", receipt).get("/validacao/buscar-hash").then().statusCode(200);
        var current = preview(code); assertEquals(1, current.respostas()); assertEquals(1, current.validacoes());
        given().contentType("application/json").body(current).delete(base(code)).then().statusCode(204);
    }

    @Test void validaConfirmacaoEEntidadeInexistente() {
        String code = create(false, false);
        given().contentType("application/json").delete(base(code)).then().statusCode(400);
        given().contentType("application/json").body("{}").delete(base(code)).then().statusCode(409);
        given().get(base("inexistente") + "/exclusao").then().statusCode(404);
        given().contentType("application/json").body(preview(code)).delete(base("inexistente")).then().statusCode(404);
        given().get(base(code)).then().statusCode(200);
    }

    @Test void exclusaoEsperaEnvioConcorrenteENaoApagaRespostaSemNovaConfirmacao() throws Exception {
        String code = create(true, true);
        var stale = preview(code);
        var saved = new CountDownLatch(1); var commit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var dto = json.readValue(submission(code), FormularioDTO.class);
            Future<?> submit = executor.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
                submissions.salvarEGerarHash(dto); saved.countDown();
                try { if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(saved.await(10, TimeUnit.SECONDS));
            Future<Integer> delete = executor.submit(() -> given().contentType("application/json").body(stale).delete(base(code)).statusCode());
            assertThrows(TimeoutException.class, () -> delete.get(300, TimeUnit.MILLISECONDS));
            commit.countDown(); submit.get(10, TimeUnit.SECONDS);
            assertEquals(409, delete.get(10, TimeUnit.SECONDS));
            assertEquals(1, preview(code).respostas());
            given().contentType("application/json").body(preview(code)).delete(base(code)).then().statusCode(204);
        } finally { commit.countDown(); executor.shutdownNow(); }
    }
}
