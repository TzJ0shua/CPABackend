package org.femass;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.femass.entity.Curso;
import org.femass.entity.Disciplina;
import org.femass.service.VerificacaoEmailService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class FormularioVersionadoResourceTest {
    @jakarta.inject.Inject CampaignTestFixtures campaignFixtures;
    @org.junit.jupiter.api.BeforeEach void scheduleFixture() { campaignFixtures.scheduleSeed(); }
    @org.junit.jupiter.api.AfterEach void clearFixture() { campaignFixtures.clearSeed(); }

    @InjectMock
    VerificacaoEmailService verificacaoEmailService;

    @jakarta.inject.Inject
    EntityManager entityManager;

    private Long disciplinaId;
    private String nomeCurso;

    @org.junit.jupiter.api.BeforeEach
    @Transactional
    void prepararDisciplinaDoCenario() {
        doNothing().when(verificacaoEmailService).consumirAutorizacaoParaEnvio(any(), any(), any(), any(), any());

        nomeCurso = "Curso de teste versionado " + UUID.randomUUID();
        Curso curso = new Curso();
        curso.setNome(nomeCurso);
        entityManager.persist(curso);

        Disciplina disciplina = new Disciplina();
        disciplina.setNome("Disciplina versionada de teste");
        disciplina.setProfessor("Professor de teste");
        disciplina.setCurso(curso);
        entityManager.persist(disciplina);
        entityManager.flush();
        disciplinaId = disciplina.getId();
    }

    @Test
    void deveListarCatalogoPorCampanhaEPublico() {
        given()
                .queryParam("campaign", "cpa-2026")
                .queryParam("publico", "aluno")
                .when().get("/formularios")
                .then().statusCode(200)
                .body("size()", equalTo(4))
                .body("find { it.code == 'discente_disciplinas' }.version", equalTo(1))
                .body("find { it.code == 'discente_gestao' }.questions.size()", equalTo(10))
                .body("find { it.code == 'discente_disciplinas' }.questions[0].options.size()", equalTo(5))
                .body("find { it.code == 'discente_disciplinas' }.questions[0].options.find { it.naoSeiResponder }.label",
                        equalTo("Não sei responder"));
    }

    @Test
    void deveAceitarRespostaVersionadaESanitizarComentario() {
        String formulario = """
                {
                  "campaign": "cpa-2026",
                  "form": "docente_disciplinas",
                  "formVersion": 1,
                  "respondent": {
                    "type": "professor",
                    "aceiteTermosCondicoesServico": true
                  },
                  "course": {"name": "%s"},
                  "subjects": [{
                    "subjectId": "%d",
                    "subjectName": "Noções Básicas de Administração",
                    "teacherName": "Professor Teste",
                    "comment": "<b>comentario</b>",
                    "answers": [{
                      "questionId": "q1",
                      "questionText": "Promove o debate e instiga o pensamento crítico, colaborando para a autonomia dos estudantes.",
                      "optionCode": "nao_sei_responder"
                    }]
                  }]
                }
                """.formatted(nomeCurso, disciplinaId);

        given().contentType("application/json").body(formulario)
                .when().post("/formulario")
                .then().statusCode(200);

        given().queryParam("publico", "professor")
                .when().get("/avaliacoes")
                .then().statusCode(200)
                .body("find { it.perguntaId == 'q1' && it.naoSeiResponder == true }.opcaoCodigo",
                        equalTo("nao_sei_responder"))
                .body("find { it.perguntaId == 'q1' && it.naoSeiResponder == true }.comentario",
                        equalTo("comentario"));
    }

    @Test
    void deveRejeitarPerguntaEOpcaoForaDoCatalogo() {
        String base = """
                {
                  "campaign": "cpa-2026",
                  "form": "docente_disciplinas",
                  "formVersion": 1,
                  "respondent": {"type": "professor", "aceiteTermosCondicoesServico": true},
                  "course": {"name": "%s"},
                  "subjects": [{"subjectId": "%d", "subjectName": "Disciplina versionada de teste", "teacherName": "Professor Teste", "answers": [{"questionId": "%s", "optionCode": "%s"}]}]
                }
                """;

        given().contentType("application/json").body(base.formatted(nomeCurso, disciplinaId, "q99", "concordo_totalmente"))
                .when().post("/formulario").then().statusCode(400);

        given().contentType("application/json").body(base.formatted(nomeCurso, disciplinaId, "q1", "opcao-inexistente"))
                .when().post("/formulario").then().statusCode(400);
    }
}
