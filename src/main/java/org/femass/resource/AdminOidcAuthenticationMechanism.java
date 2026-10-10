package org.femass.resource;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.Set;

/** Keep OIDC verification intact while rendering pre-REST administrative challenges as JSON. */
@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.oidc.enabled", stringValue = "true", enableIfMissing = true)
public class AdminOidcAuthenticationMechanism implements HttpAuthenticationMechanism {
    @Inject OidcAuthenticationMechanism delegate;
    @Override public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager manager) {
        return delegate.authenticate(context, manager);
    }
    @Override public Uni<ChallengeData> getChallenge(RoutingContext context) { return delegate.getChallenge(context); }
    @Override public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() { return delegate.getCredentialTypes(); }
    @Override public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) { return delegate.getCredentialTransport(context); }
    @Override public Uni<Boolean> sendChallenge(RoutingContext context) {
        String path = context.normalizedPath();
        if (!path.equals("/admin") && !path.startsWith("/admin/")) return delegate.sendChallenge(context);
        return Uni.createFrom().completionStage(() -> context.response().setStatusCode(401)
            .putHeader("WWW-Authenticate", "Bearer").putHeader("Content-Type", "application/json; charset=utf-8")
            .end("{\"message\":\"Autenticação necessária.\"}").toCompletionStage()).replaceWith(true);
    }
}
