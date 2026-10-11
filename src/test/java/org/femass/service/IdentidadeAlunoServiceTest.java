package org.femass.service;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class IdentidadeAlunoServiceTest {
    @Test void normalizaCpfPreservaZerosSeparaTiposECampanhas() {
        var service = new IdentidadeAlunoService();
        service.seed = Optional.of("segredo-de-teste-com-mais-de-trinta-e-dois-bytes");
        var hashes = service.calcular("529.982.247-25", "0001234567", "campanha");
        assertEquals(hashes, service.calcular("52998224725", "0001234567", "campanha"));
        assertTrue(hashes.cpf().matches("[0-9a-f]{64}"));
        assertNotEquals(hashes.cpf(), hashes.matricula());
        assertNotEquals(hashes, service.calcular("52998224725", "0001234567", "outra"));
        assertThrows(IllegalArgumentException.class, () -> service.calcular("11111111111", "0001234567", "campanha"));
        assertThrows(IllegalArgumentException.class, () -> service.calcular("abc52998224725", "0001234567", "campanha"));
        assertThrows(IllegalArgumentException.class, () -> service.calcular("52998224725", "1234567", "campanha"));
        service.seed = Optional.empty();
        assertThrows(IdentificacaoIndisponivelException.class, () -> service.calcular("52998224725", "0001234567", "campanha"));
        service.seed = Optional.of("curta");
        assertThrows(IdentificacaoIndisponivelException.class, () -> service.calcular("52998224725", "0001234567", "campanha"));
    }
}
