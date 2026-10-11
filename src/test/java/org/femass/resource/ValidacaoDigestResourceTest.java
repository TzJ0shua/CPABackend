package org.femass.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.femass.dto.ValidacaoStatusDTO;
import org.femass.entity.Validacao;
import org.femass.service.ValidacaoService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidacaoDigestResourceTest {
    private static final String DIGEST = "aaaaaaaa-aaaa-aaaa-aaaa-aa012345bcde";

    @Test
    void validacaoRetornaSomenteOsDezUltimosCaracteresDoDigestArmazenado() throws Exception {
        ValidacaoResource resource = resource();
        when(resource.service.validarCodigo("codigo-opaco")).thenReturn(validacao(DIGEST));

        try (var response = resource.validarHash("codigo-opaco", null)) {
            assertEquals(200, response.getStatus());
            var result = (ValidacaoStatusDTO) response.getEntity();
            assertEquals("codigo-opaco", result.getCodigoValidacao());
            assertEquals("012345bcde", result.getCodigoDigestFinal());
            assertEquals(Boolean.TRUE, result.getValidado());
            String json = new ObjectMapper().writeValueAsString(result);
            assertTrue(json.contains("\"codigoDigestFinal\":\"012345bcde\""));
            assertFalse(json.contains(DIGEST));
        }
        verify(resource.service, times(1)).validarCodigo("codigo-opaco");
    }

    @Test
    void historicoRetornaOSufixoDeCadaRegistroNaOrdemDoServico() throws Exception {
        ValidacaoResource resource = resource();
        when(resource.service.buscarDezUltimosCodigosValidados()).thenReturn(List.of(
            validacao(DIGEST), validacao("bbbbbbbb-bbbb-bbbb-bbbb-bb00000000af")));

        try (var response = resource.historico()) {
            assertEquals(200, response.getStatus());
            @SuppressWarnings("unchecked")
            var results = (List<ValidacaoStatusDTO>) response.getEntity();
            assertEquals(List.of("012345bcde", "00000000af"),
                results.stream().map(ValidacaoStatusDTO::getCodigoDigestFinal).toList());
            String json = new ObjectMapper().writeValueAsString(results);
            assertFalse(json.contains(DIGEST));
            assertFalse(json.contains("bbbbbbbb-bbbb-bbbb-bbbb"));
        }
    }

    @Test
    void historicoVazioContinuaRetornandoListaVazia() {
        ValidacaoResource resource = resource();
        when(resource.service.buscarDezUltimosCodigosValidados()).thenReturn(List.of());
        try (var response = resource.historico()) {
            assertEquals(200, response.getStatus());
            assertEquals(List.of(), response.getEntity());
        }
    }

    private ValidacaoResource resource() {
        var resource = new ValidacaoResource();
        resource.service = mock(ValidacaoService.class);
        resource.resumos = mock(org.femass.service.ResumoValidacaoService.class);
        return resource;
    }

    private Validacao validacao(String digest) {
        var validacao = new Validacao();
        validacao.setCodigoDigest(digest);
        validacao.setValidado(true);
        return validacao;
    }
}
