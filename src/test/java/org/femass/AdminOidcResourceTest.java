package org.femass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;

/** Exercises real discovery, signature, audience and realm-role validation; no TestSecurity bypass. */
@QuarkusTest
@TestProfile(AdminOidcResourceTest.Profile.class)
@QuarkusTestResource(value = AdminOidcResourceTest.OidcServer.class, restrictToAnnotatedClass = true)
class AdminOidcResourceTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.oidc.enabled", "true", "%test.quarkus.oidc.enabled", "true", "quarkus.keycloak.devservices.enabled", "false",
                "quarkus.http.cors.origins", "http://localhost:5173");
        }
    }
    public static class OidcServer implements QuarkusTestResourceLifecycleManager {
        private HttpServer server;
        static KeyPair keys;
        static String issuer;
        static final ObjectMapper JSON = new ObjectMapper();
        public Map<String, String> start() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair();
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/cpa";
                RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
                byte[] discovery = JSON.writeValueAsBytes(Map.of("issuer", issuer, "jwks_uri", issuer + "/certs",
                    "authorization_endpoint", issuer + "/auth", "token_endpoint", issuer + "/token", "userinfo_endpoint", issuer + "/userinfo"));
                byte[] jwks = JSON.writeValueAsBytes(Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "test-key", "use", "sig", "alg", "RS256",
                    "n", unsigned(pub.getModulus().toByteArray()), "e", unsigned(pub.getPublicExponent().toByteArray())))));
                server.createContext("/realms/cpa", exchange -> {
                    byte[] response = exchange.getRequestURI().getPath().endsWith("/certs") ? jwks : discovery;
                    exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, response.length);
                    try (var stream = exchange.getResponseBody()) { stream.write(response); }
                });
                server.start();
                return Map.of("quarkus.oidc.auth-server-url", issuer, "quarkus.oidc.token.issuer", issuer);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        private static String unsigned(byte[] bytes) { return encode(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes); }
        static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
        static String token(String role, String audience, long expires) {
            try {
                String header = encode(JSON.writeValueAsBytes(Map.of("alg", "RS256", "kid", "test-key", "typ", "JWT")));
                String payload = encode(JSON.writeValueAsBytes(Map.of("iss", issuer, "sub", "test-user", "preferred_username", "test-user",
                    "aud", audience, "exp", expires, "iat", Instant.now().getEpochSecond() - 120, "realm_access", Map.of("roles", List.of(role)))));
                String input = header + "." + payload;
                Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(keys.getPrivate()); signature.update(input.getBytes(StandardCharsets.US_ASCII));
                return input + "." + encode(signature.sign());
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        public void stop() { if (server != null) server.stop(0); }
    }
    @Test void permitePreflightDeExclusaoNaOrigemDoFrontend() {
        given().header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "DELETE")
            .header("Access-Control-Request-Headers", "authorization,content-type")
            .options("/admin/campanhas/inexistente").then().statusCode(200)
            .header("Access-Control-Allow-Origin", "http://localhost:5173")
            .header("Access-Control-Allow-Methods", containsString("DELETE"));
    }
    @Test void deveExigirTokenValidoEPerfilAdministrativoNasLeiturasEEscritas() {
        long expires = Instant.now().getEpochSecond() + 300;
        given().get("/admin/cursos").then().statusCode(401).contentType("application/json").body("message", notNullValue());
        given().get("/admin/campanhas/cpa-2026/exclusao").then().statusCode(401);
        given().contentType("application/json").body("{}").delete("/admin/campanhas/cpa-2026").then().statusCode(401);
        given().contentType("application/json").body(Map.of("nome", "Sem autenticação")).post("/admin/cursos").then().statusCode(401);
        given().contentType("application/json").body("{}").post("/admin/campanhas/cpa-2026/formularios/clonar").then().statusCode(401);
        given().header("Authorization", "Bearer inválido").get("/admin/cursos").then().statusCode(401);
        given().auth().oauth2(OidcServer.token("cpa-admin", "cpa-backend", Instant.now().getEpochSecond() - 60)).get("/admin/cursos").then().statusCode(401);
        given().auth().oauth2(OidcServer.token("cpa-admin", "outra-api", expires)).get("/admin/cursos").then().statusCode(401);
        String visitor = OidcServer.token("participante", "cpa-backend", expires);
        given().auth().oauth2(visitor).get("/admin/campanhas/cpa-2026/exclusao").then().statusCode(403);
        given().auth().oauth2(visitor).contentType("application/json").body("{}").delete("/admin/campanhas/cpa-2026").then().statusCode(403);
        given().auth().oauth2(visitor).get("/admin/cursos").then().statusCode(403).contentType("application/json").body("message", notNullValue());
        given().auth().oauth2(visitor).contentType("application/json").body(Map.of("nome", "Não permitido")).post("/admin/cursos").then().statusCode(403);
        given().auth().oauth2(visitor).contentType("application/json").body("{}").post("/admin/campanhas/cpa-2026/formularios/clonar").then().statusCode(403);
        String admin = OidcServer.token("cpa-admin", "cpa-backend", expires);
        given().auth().oauth2(admin.substring(0, admin.lastIndexOf('.') + 1) + "invalid-signature").get("/admin/cursos").then().statusCode(401);
        given().auth().oauth2(admin).get("/admin/cursos").then().statusCode(200);
        String code = "oidc-delete-" + UUID.randomUUID();
        given().auth().oauth2(admin).contentType("application/json").body(Map.of("codigo", code, "nome", "Campanha OIDC",
            "dataInicio", "2038-01-01", "dataFim", "2038-01-01")).post("/admin/campanhas").then().statusCode(201);
        String preview = given().auth().oauth2(admin).get("/admin/campanhas/" + code + "/exclusao").then().statusCode(200).extract().asString();
        given().auth().oauth2(admin).contentType("application/json").body(preview).delete("/admin/campanhas/" + code).then().statusCode(204);
        given().auth().oauth2(admin).contentType("application/json").body(Map.of("nome", "Curso OIDC " + UUID.randomUUID())).post("/admin/cursos").then().statusCode(201);
        given().get("/campanhas/cpa-2026/disponibilidade").then().statusCode(200);
    }
}
