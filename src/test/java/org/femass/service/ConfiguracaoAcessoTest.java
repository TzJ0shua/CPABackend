package org.femass.service;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguracaoAcessoTest {
    @Test void exigeGmailCompletoDigestSeguroEChaveConfigurada() {
        GmailApiEmailSender gmail = new GmailApiEmailSender();
        gmail.enabled = true; gmail.clientId = "id"; gmail.clientSecret = "secret";
        gmail.refreshToken = "refresh"; gmail.from = "remetente@example.com";
        assertTrue(gmail.configurado());
        gmail.refreshToken = "not-configured"; assertFalse(gmail.configurado());
        gmail.refreshToken = "refresh"; gmail.enabled = false; assertFalse(gmail.configurado());
        gmail.enabled = true; gmail.from = "inválido"; assertFalse(gmail.configurado());
        VerificacaoEmailService service = new VerificacaoEmailService();
        service.emailSender = mock(EmailSender.class);
        when(service.emailSender.configurado()).thenReturn(true);
        service.emailDigestSecret = "dev-only-change-me";
        service.chaveMestra = Optional.empty();
        assertFalse(service.configuracao().emailDisponivel());
        assertFalse(service.configuracao().chaveMestraDisponivel());
        service.emailDigestSecret = "segredo-teste"; service.chaveMestra = Optional.of("chave-teste");
        assertTrue(service.configuracao().emailDisponivel());
        assertTrue(service.configuracao().chaveMestraDisponivel());
        service.chaveMestra = Optional.of(" ");
        assertFalse(service.configuracao().chaveMestraDisponivel());
        assertThrows(EmailDeliveryException.class, () -> service.autorizarChaveMestra(
            new org.femass.dto.ChaveMestraDTO("qualquer", "professor", "campanha", "form", 1)));
    }
}
