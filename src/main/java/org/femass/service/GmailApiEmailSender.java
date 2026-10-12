package org.femass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class GmailApiEmailSender implements EmailSender {
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI SEND_URI = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    @Inject ObjectMapper objectMapper;
    @Inject GmailApiTransport transport;
    @ConfigProperty(name = "verificacao-email.gmail.enabled", defaultValue = "false") boolean enabled;
    @ConfigProperty(name = "verificacao-email.gmail.client-id", defaultValue = "not-configured") String clientId;
    @ConfigProperty(name = "verificacao-email.gmail.client-secret", defaultValue = "not-configured") String clientSecret;
    @ConfigProperty(name = "verificacao-email.gmail.refresh-token", defaultValue = "not-configured") String refreshToken;
    @ConfigProperty(name = "verificacao-email.gmail.from", defaultValue = "not-configured") String from;
    @ConfigProperty(name = "verificacao-email.gmail.request-timeout", defaultValue = "PT15S") Duration requestTimeout;

    @Override
    public boolean configurado() {
        return enabled && configurado(clientId) && configurado(clientSecret) && configurado(refreshToken)
                && isValidAddress(from);
    }

    private boolean configurado(String valor) {
        return !isBlank(valor) && !"not-configured".equals(valor);
    }

    @Override
    public void enviarPin(String destinatario, String pin) {
        if (!configurado() || !isValidAddress(destinatario)) {
            throw new EmailDeliveryException();
        }
        try {
            String accessToken = obterAccessToken();
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mensagemMime(destinatario, pin).getBytes(StandardCharsets.UTF_8));
            String body = objectMapper.writeValueAsString(Map.of("raw", raw));
            HttpRequest request = HttpRequest.newBuilder(SEND_URI)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new EmailDeliveryException();
        } catch (EmailDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailDeliveryException();
        }
    }

    private String obterAccessToken() throws Exception {
        String payload = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = transport.send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new EmailDeliveryException();
        JsonNode json = objectMapper.readTree(response.body());
        String accessToken = json.path("access_token").asText();
        if (isBlank(accessToken)) throw new EmailDeliveryException();
        return accessToken;
    }

    private String mensagemMime(String destinatario, String pin) {
        return "From: " + from + "\r\n"
                + "To: " + destinatario + "\r\n"
                + "Subject: Codigo de verificacao CPA FEMASS\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                + "Seu codigo de verificacao e: " + pin + "\r\n\r\n"
                + "Ele expira em 2 horas e pode ser usado uma unica vez.";
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private boolean isValidAddress(String value) {
        return !isBlank(value) && value.matches("^[^\\s@\\r\\n]+@[^\\s@\\r\\n]+$");
    }
}
