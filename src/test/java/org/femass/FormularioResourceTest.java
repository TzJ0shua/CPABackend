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
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.ByteBuffer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FormularioResourceTest {
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
    void liberarVerificacaoParaCenariosLegados() {
        doNothing().when(verificacaoEmailService).consumirAutorizacaoParaEnvio(any(), any(), any(), any(), any());

        nomeCurso = "Curso de teste issue 8 " + UUID.randomUUID();
        Curso curso = new Curso();
        curso.setNome(nomeCurso);
        entityManager.persist(curso);

        Disciplina disciplina = new Disciplina();
        disciplina.setNome("Disciplina de teste issue 8");
        disciplina.setProfessor("Professor de teste");
        disciplina.setCurso(curso);
        entityManager.persist(disciplina);
        entityManager.flush();
        disciplinaId = disciplina.getId();
    }

    @Test
    void deveRejeitarPayloadSemMetadadosDoFormulario() {
        given().contentType("application/json").body("""
                {"respondent":{"type":"aluno","aceiteTermosCondicoesServico":true}}
                """).when().post("/formulario").then().statusCode(400);
    }

    @Test
    void deveSalvarFormularioDisciplinarVersionado() throws Exception {
        String formulario = """
                {
                  "campaign":"cpa-2026", "form":"discente_disciplinas", "formVersion":1,
                  "respondent":{"type":"aluno","aceiteTermosCondicoesServico":true},
                  "course":{"name":"%s"},
                  "subjects":[{"subjectId":"%d","answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]}]
                }
                """.formatted(nomeCurso, disciplinaId);

        Map<String, String> comprovante = given().contentType("application/json").body(formulario)
                .when().post("/formulario").then().statusCode(200).body("codigoValidacao", notNullValue())
                .extract().as(Map.class);
        String codigo = comprovante.get("codigoValidacao");
        ByteBuffer bytes = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
        UUID digest = new UUID(bytes.getLong(), bytes.getLong());
        String identificador = digest.toString().substring(26);
        assertEquals(codigo, comprovante.get("qrCode"));
        assertEquals(identificador, comprovante.get("codigoDigestFinal"));
        assertFalse(comprovante.containsKey("codigoDigest"));
        given().queryParam("codigoValidacao", codigo)
                .when().put("/validacao/validar-hash").then().statusCode(200)
                .body("codigoDigestFinal", org.hamcrest.Matchers.is(identificador));
    }

    @Test
    void deveSalvarFormularioGeralSemDisciplina() {
        given().contentType("application/json").body("""
                {
                  "campaign":"cpa-2026", "form":"funcionario_gestao", "formVersion":1,
                  "respondent":{"type":"funcionario","aceiteTermosCondicoesServico":true},
                  "answers":[{"questionId":"q1","optionCode":"nao_sei_responder"}]
                }
                """).when().post("/formulario").then().statusCode(200);

        given().queryParam("publico", "funcionario").when().get("/avaliacoes")
                .then().statusCode(200).body("find { it.naoSeiResponder }.disciplina", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void deveDescartarIdentificadoresLegadosDoAluno() {
        given().contentType("application/json").body("""
                {
                  "campaign":"cpa-2026", "form":"discente_gestao", "formVersion":1,
                  "respondent":{
                    "type":"aluno",
                    "aceiteTermosCondicoesServico":true,
                    "cpf":"529.982.247-25",
                    "matricula":"legado",
                    "email":"aluno@femass.edu.br"
                  },
                  "answers":[{"questionId":"q1","optionCode":"concordo_totalmente"}]
                }
                """).when().post("/formulario").then().statusCode(200);
    }
}
