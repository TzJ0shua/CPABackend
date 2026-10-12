package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.femass.entity.EstadoCampanha;
import org.femass.service.DisponibilidadeCampanhaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestSecurity(user = "comissao", roles = "cpa-admin")
class AdminPublishedFormEditResourceTest {
    @Inject EntityManager em;
    @Inject DisponibilidadeCampanhaService disponibilidade;
    private final List<String> campaigns = new ArrayList<>();
    private static final String QUESTIONS = """
        [{"codigo":"q1","texto":"Pergunta original","ordem":1,"ativo":true,
          "opcoes":[{"code":"sim","label":"Sim","value":1,"ativo":true}]},
         {"codigo":"q2","texto":"Outra pergunta","ordem":2,"ativo":true,
          "opcoes":[{"code":"sim","label":"Sim","value":1,"ativo":true}]}]
        """;

    private static String form(String questions) {
        return """
            {"codigo":"geral","nome":"Avaliação","publico":"aluno","escopo":"GERAL",
             "ordem":1,"commentAllowed":false,"perguntas":%s}
            """.formatted(questions);
    }
    private static String base(String campaign) { return "/admin/campanhas/" + campaign; }
    private static String version(String campaign) { return base(campaign) + "/formularios/geral/versoes/1"; }

    private String published(EstadoCampanha state) {
        String code = "edit-published-" + System.nanoTime();
        campaigns.add(code);
        given().contentType("application/json").body("""
            {"codigo":"%s","nome":"Campanha de edição","dataInicio":"%s","dataFim":"%s"}
            """.formatted(code, disponibilidade.hoje(), disponibilidade.hoje()))
            .post("/admin/campanhas").then().statusCode(201);
        given().contentType("application/json").body(form(QUESTIONS)).post(base(code) + "/formularios").then().statusCode(201);
        given().post(version(code) + "/publicar").then().statusCode(204);
        if (state == EstadoCampanha.APROVADA || state == EstadoCampanha.ABERTA)
            given().post(base(code) + "/aprovar").then().statusCode(204);
        if (state == EstadoCampanha.ABERTA) given().post(base(code) + "/abrir").then().statusCode(204);
        if (state == EstadoCampanha.ENCERRADA) given().post(base(code) + "/encerrar").then().statusCode(204);
        return code;
    }

    @AfterEach void closeCampaigns() {
        for (String code : campaigns) given().post(base(code) + "/encerrar").then().statusCode(204);
    }

    @ParameterizedTest
    @EnumSource(EstadoCampanha.class)
    void editaMesmaVersaoSemModificarEstadoDaCampanha(EstadoCampanha state) {
        String code = published(state);
        var original = given().get(version(code)).then().extract().jsonPath();
        long versionId = original.getLong("id");
        long questionId = original.getLong("perguntas[0].id");
        long optionId = original.getLong("perguntas[0].opcoes[0].id");
        String updated = form(QUESTIONS.replace("Pergunta original", "Pergunta atualizada"))
            .replace("\"nome\":\"Avaliação\"", "\"nome\":\"Avaliação atualizada\"")
            .replace("\"publico\":\"aluno\"", "\"publico\":\"funcionario\"")
            .replace("\"escopo\":\"GERAL\"", "\"escopo\":\"DISCIPLINA\"")
            .replace("\"commentAllowed\":false", "\"commentAllowed\":true,\"commentNotice\":\"Comentário opcional.\"");
        given().contentType("application/json").body(updated).put(version(code)).then().statusCode(204);
        given().get(version(code)).then().statusCode(200)
            .body("id", is((int) versionId)).body("numero", is(1)).body("estado", is("PUBLICADA"))
            .body("nome", is("Avaliação atualizada")).body("publico", is("funcionario"))
            .body("escopo", is("DISCIPLINA")).body("commentAllowed", is(true))
            .body("perguntas[0].id", is((int) questionId)).body("perguntas[0].texto", is("Pergunta atualizada"))
            .body("perguntas[0].opcoes[0].id", is((int) optionId));
        given().get(base(code)).then().body("estado", is(state.name()));
        given().get(base(code) + "/formularios/geral/versoes").then().body("size()", is(1));
        if (state == EstadoCampanha.ABERTA) {
            given().queryParam("campaign", code).queryParam("publico", "funcionario").get("/formularios")
                .then().statusCode(200).body("[0].version", is(1)).body("[0].name", is("Avaliação atualizada"))
                .body("[0].questions[0].texto", is("Pergunta atualizada"));
            given().get("/campanhas/" + code + "/disponibilidade").then().body("disponivelParaResposta", is(true));
        }
    }

    private static String submission(String code, String cpf, String enrollment, boolean updated) {
        String answers = updated ? """
            [{"questionId":"q1","questionText":"Pergunta atualizada","optionCode":"sim"},
             {"questionId":"q3","optionCode":"sim"}]
            """ : """
            [{"questionId":"q1","optionCode":"sim"},{"questionId":"q2","optionCode":"sim"}]
            """;
        return """
            {"campaign":"%s","form":"geral","formVersion":1,
             "respondent":{"type":"aluno","aceiteTermosCondicoesServico":true,"cpf":"%s","matricula":"%s"},
             "answers":%s}
            """.formatted(code, cpf, enrollment, answers);
    }

    @Test void preservaRespostasVinculosELimiteDeParticipacaoAoEditarERemoverPerguntas() {
        String code = published(EstadoCampanha.ABERTA);
        given().contentType("application/json").body(submission(code, "52998224725", "0000000001", false))
            .post("/formulario").then().statusCode(200);
        var original = given().get(version(code)).then().extract().jsonPath();
        long versionId = original.getLong("id");
        long questionId = original.getLong("perguntas[0].id");
        long removedId = original.getLong("perguntas[1].id");
        long optionId = original.getLong("perguntas[0].opcoes[0].id");
        String updatedQuestions = """
            [{"codigo":"q1","texto":"Pergunta atualizada","ordem":1,"ativo":true,
              "opcoes":[{"code":"sim","label":"Concordo","value":4,"ativo":true}]},
             {"codigo":"q3","texto":"Nova pergunta","ordem":3,"ativo":true,
              "opcoes":[{"code":"sim","label":"Sim","value":1,"ativo":true}]}]
            """;
        given().contentType("application/json").body(form(updatedQuestions)).put(version(code)).then().statusCode(204);
        given().get(version(code)).then().body("id", is((int) versionId))
            .body("perguntas.find { it.codigo == 'q1' }.id", is((int) questionId))
            .body("perguntas.find { it.codigo == 'q1' }.opcoes[0].id", is((int) optionId))
            .body("perguntas.find { it.codigo == 'q2' }.id", is((int) removedId))
            .body("perguntas.find { it.codigo == 'q2' }.ativo", is(false));
        assertEquals(2L, em.createQuery("select count(r) from Resposta r where r.avaliacao.formularioVersao.id = :id", Long.class)
            .setParameter("id", versionId).getSingleResult());
        assertEquals("Pergunta atualizada", em.createQuery("select r.perguntaVersao.texto from Resposta r where r.perguntaVersao.id = :id", String.class)
            .setParameter("id", questionId).getSingleResult());
        assertEquals(1, em.createQuery("select r.nota from Resposta r where r.perguntaVersao.id = :id", Integer.class)
            .setParameter("id", questionId).getSingleResult());
        assertEquals("Sim", em.createQuery("select r.opcaoRotulo from Resposta r where r.perguntaVersao.id = :id", String.class)
            .setParameter("id", questionId).getSingleResult());
        given().queryParam("campaign", code).queryParam("publico", "aluno").get("/formularios")
            .then().statusCode(200).body("[0].questions.id", contains("q1", "q3"));
        given().contentType("application/json").body(submission(code, "52998224725", "0000000001", true))
            .post("/formulario").then().statusCode(409).body("code", is("FORMULARIO_JA_RESPONDIDO"));
        given().contentType("application/json").body(submission(code, "11144477735", "0000000002", true))
            .post("/formulario").then().statusCode(200);
    }

    @Test void rejeitaQuestionarioPublicadoInvalidoSemSalvarAlteracoesParciais() {
        String code = published(EstadoCampanha.ABERTA);
        var original = given().get(version(code)).then().extract().jsonPath().getMap("");
        for (String questions : List.of("[]", QUESTIONS.replace("\"ativo\":true", "\"ativo\":false"),
            QUESTIONS.replace("\"value\":1,\"ativo\":true", "\"value\":1,\"ativo\":false"))) {
            given().contentType("application/json").body(form(questions).replace("Avaliação", "Nome inválido"))
                .put(version(code)).then().statusCode(400);
            assertEquals(original, given().get(version(code)).then().extract().jsonPath().getMap(""));
            given().get(base(code)).then().body("estado", is("ABERTA"));
        }
    }
}
