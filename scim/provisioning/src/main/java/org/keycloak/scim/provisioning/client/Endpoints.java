package org.keycloak.scim.provisioning.client;

import java.time.Duration;
import java.util.function.Supplier;

import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import org.apache.http.client.HttpClient;

/**
 * Builds a destination's {@link ScimEndpoint} on Keycloak's shared HTTP client, so the outgoing
 * calls use the server's proxy, truststore and connection pool settings. The client is
 * node-wide: the endpoint stays usable after the session that built it is closed.
 */
public final class Endpoints {
    private Endpoints() {}

    /** For callers that hold no session, such as a reconcile paging the app outside a transaction. */
    public static ScimEndpoint create(
            KeycloakSessionFactory factory, String baseUrl, Supplier<String> token, Duration requestTimeout) {
        HttpClient http;
        try (KeycloakSession session = factory.create()) {
            http = session.getProvider(HttpClientProvider.class).getHttpClient();
        }
        return ScimEndpoint.create(http, baseUrl, token, requestTimeout);
    }

    public static ScimEndpoint create(
            KeycloakSession session, String baseUrl, Supplier<String> token, Duration requestTimeout) {
        return ScimEndpoint.create(
                session.getProvider(HttpClientProvider.class).getHttpClient(), baseUrl, token, requestTimeout);
    }
}
