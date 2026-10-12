package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.entity.EstadoCampanha;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class AdminFormCloneResourceTest {
    @Inject EntityManager em;
    private static final String QUESTIONS = """
      [{"codigo":"q1","texto":"Pergunta histórica","ordem":1,"ativo":true,"opcoes":[{"code":"sim","label":"Sim","value":1,"ativo":true},{"code":"antiga","label":"Opção antiga","value":2,"ativo":false}]},
       {"codigo":"q2","texto":"Pergunta desativada","ordem":2,"ativo":false,"opcoes":[{"code":"sim","label":"Sim","value":1}]}]
      """;
    private static String form(String questions) {
        return """
          {"codigo":"geral","nome":"Formulário histórico","publico":"aluno","escopo":"GERAL","ordem":3,
           "commentAllowed":true,"commentNotice":"Comentário opcional.","perguntas":%s}
          """.formatted(questions);
    }
    private String campaign() {
        String code = "clone-" + UUID.randomUUID();
        var today = LocalDate.now(org.femass.service.DisponibilidadeCampanhaService.ZONE);
        given().contentType("application/json").body(Map.of("codigo", code, "nome", code, "dataInicio", today.toString(), "dataFim", today.toString()))
            .post("/admin/campanhas").then().statusCode(201);
        return code;
    }
    private static String base(String code) { return "/admin/campanhas/" + code; }
    private static Map<String,Object> clone(String source, int version, String code) {
        return Map.of("campanhaOrigem", source, "formularioOrigem", "geral", "versaoOrigem", version, "codigo", code);
    }

    @Test void copiaVersaoEscolhidaDaCampanhaAntigaERemovePerguntasSemApagarHistorico() {
        String source = campaign(); String sourceBase = base(source);
        given().contentType("application/json").body(form(QUESTIONS)).post(sourceBase + "/formularios").then().statusCode(201);
        String sourceVersion = sourceBase + "/formularios/geral/versoes/1";
        given().post(sourceVersion + "/publicar").then().statusCode(204);
        given().post(sourceBase + "/aprovar").then().statusCode(204);
        given().post(sourceBase + "/abrir").then().statusCode(204);
        given().contentType("application/json").body("""
            {"campaign":"%s","form":"geral","formVersion":1,"respondent":{"type":"aluno","aceiteTermosCondicoesServico":true,"cpf":"52998224725","matricula":"0000000001"},"answers":[{"questionId":"q1","optionCode":"sim"}]}
            """.formatted(source)).post("/formulario").then().statusCode(200);
        // A newer draft exists, but the request explicitly selects published version 1.
        given().post(sourceBase + "/formularios/geral/versoes").then().statusCode(201);
        given().contentType("application/json").body(form(QUESTIONS.replace("Pergunta histórica", "Texto da versão mais recente")))
            .put(sourceBase + "/formularios/geral/versoes/2").then().statusCode(204);
        given().post(sourceBase + "/encerrar").then().statusCode(204);
        given().contentType("application/json").body(Map.of("ativo", false)).patch(sourceBase + "/status").then().statusCode(204);
        given().contentType("application/json").body(Map.of("ativo", false)).patch(sourceBase + "/formularios/geral/status").then().statusCode(204);
        given().contentType("application/json").body(Map.of("ativo", false)).patch(sourceVersion + "/status").then().statusCode(204);
        var snapshot = given().get(sourceVersion).then().statusCode(200).extract().jsonPath().getMap("");
        var sourceCampaign = given().get(sourceBase).then().extract().jsonPath().getMap("");
        long sourceQuestionId = given().get(sourceVersion).then().extract().jsonPath().getLong("perguntas[0].id");
        long answers = em.createQuery("select count(r) from Resposta r where r.perguntaVersao.id = :id", Long.class)
            .setParameter("id", sourceQuestionId).getSingleResult();
        assertEquals(1, answers);
        String target = campaign();
        QuarkusTransaction.requiringNew().run(() -> em.createQuery("update Campanha set estado = :estado where codigo = :codigo")
            .setParameter("estado", EstadoCampanha.APROVADA).setParameter("codigo", target).executeUpdate());
        String endpoint = base(target) + "/formularios/clonar";
        given().contentType("application/json").body(clone(source, 1, "copia")).post(endpoint).then().statusCode(201)
            .body("codigo", is("copia")).body("versao", is(1));
        String copy = base(target) + "/formularios/copia/versoes/1";
        var copied = given().get(copy).then().statusCode(200).body("estado", is("RASCUNHO")).body("ativo", is(true))
            .body("ordem", is(3)).body("escopo", is("GERAL")).body("commentNotice", is("Comentário opcional."))
            .body("perguntas[0].texto", is("Pergunta histórica")).body("perguntas[1].ativo", is(false))
            .body("perguntas[0].opcoes[1].ativo", is(false)).extract().jsonPath();
        long questionId = copied.getLong("perguntas[0].id");
        long optionId = copied.getLong("perguntas[0].opcoes[0].id");
        assertNotEquals(sourceQuestionId, questionId);
        given().get(base(target)).then().body("estado", is("RASCUNHO"));
        // Omitted items are deactivated, and an empty draft is allowed until publication.
        given().contentType("application/json").body(form("[]").replace("\"geral\"", "\"copia\""))
            .put(copy).then().statusCode(204);
        given().get(copy).then().body("perguntas[0].id", is((int) questionId)).body("perguntas[0].ativo", is(false))
            .body("perguntas[0].opcoes[0].id", is((int) optionId));
        given().post(copy + "/publicar").then().statusCode(400);
        given().contentType("application/json").body(form("[]")).put(sourceVersion).then().statusCode(400);
        assertEquals(snapshot, given().get(sourceVersion).then().extract().jsonPath().getMap(""));
        assertEquals(sourceCampaign, given().get(sourceBase).then().extract().jsonPath().getMap(""));
        assertEquals(answers, em.createQuery("select count(r) from Resposta r where r.perguntaVersao.id = :id", Long.class)
            .setParameter("id", sourceQuestionId).getSingleResult());
    }

    @Test void validaOrigemDestinoVersaoEUnicidade() {
        String source = campaign(); String target = campaign(); String endpoint = base(target) + "/formularios/clonar";
        given().contentType("application/json").body(form(QUESTIONS)).post(base(source) + "/formularios").then().statusCode(201);
        given().contentType("application/json").body("{}").post(endpoint).then().statusCode(400);
        given().contentType("application/json").body(clone(source, 0, "copia")).post(endpoint).then().statusCode(400);
        given().contentType("application/json").body(clone(target, 1, "copia")).post(endpoint).then().statusCode(400);
        given().contentType("application/json").body(clone("inexistente", 1, "copia")).post(endpoint).then().statusCode(404);
        given().contentType("application/json").body(clone(source, 99, "copia")).post(endpoint).then().statusCode(404);
        given().contentType("application/json").body(clone(source, 1, "copia")).post(endpoint).then().statusCode(201);
        given().contentType("application/json").body(clone(source, 1, "copia")).post(endpoint).then().statusCode(409);
        given().get(base(target) + "/formularios").then().body("size()", is(1));
        given().post(base(target) + "/encerrar").then().statusCode(204);
        given().contentType("application/json").body(clone(source, 1, "outra")).post(endpoint).then().statusCode(409);
        given().contentType("application/json").body(clone(source, 1, "outra")).post("/admin/campanhas/inexistente/formularios/clonar").then().statusCode(404);
    }
}
