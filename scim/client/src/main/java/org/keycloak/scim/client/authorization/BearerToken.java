package org.keycloak.scim.client.authorization;

import java.util.function.Supplier;

import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;

import org.apache.http.HttpHeaders;

/**
 * A static bearer token, such as an API token issued by the SCIM service. The token is read from
 * the supplier on every request, so a vault value or a rotated secret takes effect at once.
 */
public record BearerToken(Supplier<String> token) implements AuthorizationMethod {

    @Override
    public void onBefore(SimpleHttp http, SimpleHttpRequest request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get());
    }
}
