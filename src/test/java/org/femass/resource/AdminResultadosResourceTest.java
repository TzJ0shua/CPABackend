package org.femass.resource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.femass.entity.*;
import org.femass.service.AdministracaoCampanhaService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminResultadosResourceTest {
    @Test
    @SuppressWarnings("unchecked")
    void separaCampanhaEPreservaAnonimatoSemContarDisciplinasComoPessoas() {
        var resource = new AdminResultadosResource();
        resource.em = mock(EntityManager.class);
        resource.campanhas = mock(AdministracaoCampanhaService.class);
        TypedQuery<Avaliacao> query = mock(TypedQuery.class);
        when(resource.em.createQuery(anyString(), eq(Avaliacao.class))).thenReturn(query);
        when(query.setParameter("codigo", "outubro")).thenReturn(query);
        var form = new Formulario(); form.setCodigo("geral"); form.setNome("Instituição");
        var version = new FormularioVersao(); version.setFormulario(form); version.setNumero(1);
        var question = new PerguntaVersao(); question.setCodigo("q1"); question.setTexto("Pergunta");
        var first = new Avaliacao(); first.setId(1L); first.setFormularioVersao(version);
        var second = new Avaliacao(); second.setId(2L); second.setFormularioVersao(version);
        var third = new Avaliacao(); third.setId(3L); third.setFormularioVersao(version);
        for (var a : List.of(first, second, third)) {
            var validation = new Validacao(); validation.setId(a.getId());
            validation.setCpfHash(a == third ? "outra-pessoa" : "mesma-pessoa");
            if (a == third) validation.setOldToken("CPA_DEMO_RESULTADOS_20261019_01");
            a.setValidacao(validation);
            var answer = new Resposta(); answer.setPerguntaVersao(question);
            answer.setOpcaoRotulo("Concordo"); answer.setNota(5);
            a.setRespostas(List.of(answer));
        }
        when(query.getResultList()).thenReturn(List.of(first, second, third));
        var result = resource.resultados("outubro");
        verify(resource.campanhas).consultar("outubro");
        verify(query).setParameter("codigo", "outubro");
        assertEquals(3, result.size());
        assertEquals(result.get(0).participante(), result.get(1).participante());
        assertNotEquals(result.get(0).participante(), result.get(2).participante());
        assertFalse(result.toString().contains("mesma-pessoa"));
        assertEquals("Instituição", result.get(0).formularioNome());
        assertEquals(5, result.get(0).nota());
        assertFalse(result.get(0).demonstracao());
        assertTrue(result.get(2).demonstracao());
    }
}
