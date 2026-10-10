package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.femass.entity.*;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class AdminCadastrosResourceTest {
    @Inject EntityManager em;
    private static String unique() { return "teste-" + UUID.randomUUID(); }
    private static String form(String questions) {
        return """
          {"codigo":"geral","nome":"Avaliação","publico":"aluno","escopo":"GERAL","ordem":1,"perguntas":%s}
          """.formatted(questions);
    }
    private static final String QUESTIONS = """
        [{"codigo":"q1","texto":"Pergunta um","ordem":1,"opcoes":[{"code":"sim","label":"Sim","value":1},{"code":"nao_sei","label":"Não sei responder","value":null,"naoSeiResponder":true}]},
         {"codigo":"q2","texto":"Pergunta dois","ordem":2,"opcoes":[{"code":"sim","label":"Sim","value":1}]}]
        """;

    @Test void deveManterCadastrosDesativadosEAplicarHierarquia() {
        String nome = unique();
        long curso = given().contentType("application/json").body(java.util.Map.of("nome", nome)).post("/admin/cursos")
            .then().statusCode(201).body("ativo", is(true)).extract().jsonPath().getLong("id");
        long disciplina = given().contentType("application/json").body(java.util.Map.of("nome", "Disciplina", "professor", "Professor", "cursoId", curso))
            .post("/admin/disciplinas").then().statusCode(201).extract().jsonPath().getLong("id");
        String submission = """
            {"campaign":"cpa-2026","form":"discente_disciplinas","formVersion":1,
             "respondent":{"type":"aluno","aceiteTermosCondicoesServico":true},"course":{"name":"%s"},
             "subjects":[{"subjectId":"%d","answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}]}
            """.formatted(nome, disciplina);
        given().contentType("application/json").body(submission).post("/formulario").then().statusCode(200);
        given().contentType("application/json").body(java.util.Map.of("nome", nome)).post("/admin/cursos").then().statusCode(409).body("message", notNullValue());
        status("/admin/cursos/" + curso, false);
        given().contentType("application/json").body(submission).post("/formulario").then().statusCode(400);
        given().get("/admin/cursos/" + curso).then().statusCode(200).body("ativo", is(false));
        given().get("/admin/disciplinas/" + disciplina).then().statusCode(200).body("ativo", is(true)).body("cursoAtivo", is(false));
        given().get("/dados-formulario?campaign=cpa-2026&publico=aluno").then().statusCode(200).body("cursos.id", not(hasItem((int) curso)));
        status("/admin/disciplinas/" + disciplina, false);
        status("/admin/cursos/" + curso, true);
        given().contentType("application/json").body(submission).post("/formulario").then().statusCode(400);
        given().get("/dados-formulario?campaign=cpa-2026&publico=aluno").then().statusCode(200)
            .body("cursos.find { it.id == " + curso + " }.disciplinas", empty());
        status("/admin/disciplinas/" + disciplina, true);
        given().contentType("application/json").body(submission).post("/formulario").then().statusCode(200);
        given().get("/dados-formulario?campaign=cpa-2026&publico=aluno").then().statusCode(200)
            .body("cursos.find { it.id == " + curso + " }.disciplinas.id", hasItem((int) disciplina));
        given().contentType("application/json").body("{}").patch("/admin/cursos/" + curso + "/status").then().statusCode(400);
        given().get("/admin/cursos/9223372036854775807").then().statusCode(404);
    }
    @Test void devePreservarIdsRascunhosHistoricoEImutabilidade() {
        String campaign = unique();
        String base = "/admin/campanhas/" + campaign;
        given().contentType("application/json").body(java.util.Map.of("codigo", campaign, "nome", "Campanha"))
            .post("/admin/campanhas").then().statusCode(201);
        given().contentType("application/json").body(form(QUESTIONS)).post(base + "/formularios").then().statusCode(201);
        String version = base + "/formularios/geral/versoes/1";
        long questionId = given().get(version).then().statusCode(200).extract().jsonPath().getLong("perguntas[0].id");
        long optionId = given().get(version).then().statusCode(200).extract().jsonPath().getLong("perguntas[0].opcoes[0].id");
        given().contentType("application/json").body(form("""
          [{"codigo":"q1","texto":"Texto atualizado","ordem":1,"opcoes":[{"code":"sim","label":"Sim atualizado","value":2}]}]
          """)).put(version).then().statusCode(204);
        given().get(version).then().statusCode(200).body("perguntas.find { it.codigo == 'q1' }.id", is((int) questionId))
            .body("perguntas.find { it.codigo == 'q1' }.opcoes[0].id", is((int) optionId))
            .body("perguntas.find { it.codigo == 'q1' }.opcoes.find { it.code == 'nao_sei' }.ativo", is(false))
            .body("perguntas.find { it.codigo == 'q2' }.ativo", is(false));
        given().post(version + "/publicar").then().statusCode(204);
        given().contentType("application/json").body(form(QUESTIONS)).put(version).then().statusCode(409);
        given().post(base + "/aprovar").then().statusCode(204);
        given().post(base + "/abrir").then().statusCode(204);
        given().get("/formularios?campaign=" + campaign + "&publico=aluno").then().statusCode(200).body("[0].questions.size()", is(1));
        given().contentType("application/json").body("""
            {"campaign":"%s","form":"geral","formVersion":1,"respondent":{"type":"aluno","aceiteTermosCondicoesServico":true},"answers":[{"questionId":"q1","optionCode":"sim"}]}
            """.formatted(campaign)).post("/formulario").then().statusCode(200);
        long answers = em.createQuery("select count(r) from Resposta r where r.perguntaVersao.id = :id", Long.class).setParameter("id", questionId).getSingleResult();
        assertEquals(1, answers);
        status(version, false);
        given().get("/formularios?campaign=" + campaign + "&publico=aluno").then().statusCode(200).body("size()", is(0));
        given().contentType("application/json").body("""
            {"campaign":"%s","form":"geral","formVersion":1,"respondent":{"type":"aluno","aceiteTermosCondicoesServico":true},"answers":[{"questionId":"q1","optionCode":"sim"}]}
            """.formatted(campaign)).post("/formulario").then().statusCode(400);
        assertEquals(answers, em.createQuery("select count(r) from Resposta r where r.perguntaVersao.id = :id", Long.class).setParameter("id", questionId).getSingleResult());
        status(base, false); status(version, true);
        given().get("/campanhas/" + campaign + "/disponibilidade").then().statusCode(200).body("disponivelParaResposta", is(false));
        status(base, true);
        status(base + "/formularios/geral", false);
        given().get("/formularios?campaign=" + campaign + "&publico=aluno").then().statusCode(200).body("size()", is(0));
        status(base + "/formularios/geral", true);
        given().post(base + "/formularios/geral/versoes").then().statusCode(201).body(is("2"));
        given().get(base + "/formularios/geral/versoes/2").then().statusCode(200).body("estado", is("RASCUNHO"))
            .body("perguntas.find { it.codigo == 'q2' }.ativo", is(false));
    }
    @Test void naoDevePublicarSemPerguntasAtivasNemPersistirEdicaoInvalida() {
        String campaign = unique(); String base = "/admin/campanhas/" + campaign;
        given().contentType("application/json").body(java.util.Map.of("codigo", campaign, "nome", "Campanha" )).post("/admin/campanhas").then().statusCode(201);
        given().contentType("application/json").body(form(QUESTIONS)).post(base + "/formularios").then().statusCode(201);
        String version = base + "/formularios/geral/versoes/1";
        given().contentType("application/json").body(form("""
            [{"codigo":"q1","texto":"Pergunta","ativo":false,"opcoes":[{"code":"sim","label":"Sim","value":1}]}]
            """)).put(version).then().statusCode(204);
        given().post(version + "/publicar").then().statusCode(400);
        given().contentType("application/json").body(form("""
            [{"codigo":"q1","texto":"Inválida","opcoes":[{"code":"sim","label":"Sim","value":9}]}]
            """)).put(version).then().statusCode(400);
        given().get(version).then().statusCode(200).body("perguntas.find { it.codigo == 'q1' }.texto", is("Pergunta"));
    }
    private static void status(String path, boolean active) {
        given().contentType("application/json").body(java.util.Map.of("ativo", active)).patch(path + "/status").then().statusCode(204);
    }
}
