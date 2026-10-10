package org.keycloak.scim.client;


import java.io.IOException;

import jakarta.ws.rs.core.Response.Status;

import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.scim.client.authorization.AuthorizationMethod;
import org.keycloak.scim.protocol.response.ErrorResponse;
import org.keycloak.scim.resource.ResourceTypeRepresentation;
import org.keycloak.scim.resource.config.ServiceProviderConfig;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;

/**
 * <p>A client for interacting with a SCIM 2.0 compliant server. This client provides methods for performing
 * CRUD operations on SCIM resources such as users and groups, as well as retrieving the service provider configuration.
 *
 * <p>This client is not designed as a standalone SCIM client library, but rather for internal use within Keycloak for testing and integration purposes.
 * It is built on top of {@link SimpleHttp} client.
 *
 * <p>In order to create instances of this client you will need a {@link HttpClient} instance. At runtime, this instance
 * is available from the {@link org.keycloak.connections.httpclient.HttpClientProvider} provider.
 *
 * <p>Example usage:
 * <pre>
 * try (ScimClient scimClient = ScimClient.create(httpClient)
 *         .withBaseUrl("https://scim.example.com")
 *         .withAuthorization(new ScimClient.Builder.OAuth2Bearer("https://auth.examplecom/realms/master/protocol/openid-connect/token", "client-id", "client-secret"))
 *         .build()) {
 *     // Use scimClient to perform operations, e.g.:
 *     ScimUser user = scimClient.users().get("user-id");
 * }
 * </pre>
 */
public final class ScimClient implements AutoCloseable {

    private static final String APPLICATION_SCIM_JSON = "application/scim+json";
    private static final String ACCEPT = APPLICATION_SCIM_JSON + ", application/json";

    private final SimpleHttp http;
    private String baseUrl;
    private AuthorizationMethod authorizationMethod;

    private ScimClient(HttpClient http) {
        this.http = SimpleHttp.create(http);
    }

    private void setRequestConfig(RequestConfig requestConfig) {
        http.withRequestConfig(requestConfig);
    }

    public static Builder create(HttpClient httpClient) {
        return new Builder(httpClient);
    }

    public ScimUsersClient users() {
        return new ScimUsersClient(this);
    }

    public ScimGroupsClient groups() {
        return new ScimGroupsClient(this);
    }

    public ScimConfigClient config() {
        return new ScimConfigClient(this);
    }

    public ScimResourceTypesClient resourceTypes() {
        return new ScimResourceTypesClient(this);
    }

    public ScimSchemasClient schemas() {
        return new ScimSchemasClient(this);
    }

    /**
     * Execute a raw GET request to the given path relative to the SCIM base URL.
     * Throws {@link ScimClientException} if the response status is not successful.
     *
     * @param path the path relative to the SCIM base URL (e.g., "InvalidType")
     */
    public void get(String path) {
        execute(doGet(path));
    }

    @Override
    public void close() {
        // no-op for now
    }

    SimpleHttpResponse execute(SimpleHttpRequest request) throws ScimClientException {
        try {
            SimpleHttpResponse response = request.asResponse();

            int status = response.getStatus();

            if (!Status.Family.familyOf(status).equals(Status.Family.SUCCESSFUL)) {
                String retryAfter = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
                String payload = response.asString();

                try (response) {
                    ErrorResponse error = response.asJson(ErrorResponse.class);
                    throw new ScimClientException("Error response from SCIM server", error, status, retryAfter);
                } catch (ScimClientException sce) {
                    throw sce;
                } catch (Exception e) {
                    throw new ScimClientException("Unexpected error response from SCIM server", new ErrorResponse(payload, status), status, retryAfter);
                }
            }

            return response;
        } catch (ScimClientException sce) {
            throw sce;
        } catch (Exception e) {
            throw new ScimClientException("Unexpected response from SCIM server", e);
        }
    }

    SimpleHttpRequest doGet(String path) {
        return beforeRequest(http.doGet(baseUrl + path))
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    SimpleHttpRequest doGet(Class<? extends ResourceTypeRepresentation> resourceType, String path) {
        return beforeRequest(http.doGet(baseUrl + getResourceTypePath(resourceType) + path))
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    SimpleHttpRequest doGet(Class<? extends ResourceTypeRepresentation> resourceType) {
        return beforeRequest(http.doGet(baseUrl + getResourceTypePath(resourceType)))
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    SimpleHttpRequest doPost(Class<? extends ResourceTypeRepresentation> resourceType) {
        return beforeRequest(http.doPost(baseUrl + getResourceTypePath(resourceType)))
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_SCIM_JSON)
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    SimpleHttpRequest doPost(Class<? extends ResourceTypeRepresentation> resourceType, String path) {
        return beforeRequest(http.doPost(baseUrl + getResourceTypePath(resourceType) + path))
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_SCIM_JSON)
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    private String getResourceTypePath(Class<? extends ResourceTypeRepresentation> resourceType) {
        String path = "/" + resourceType.getSimpleName();

        if (resourceType.equals(ServiceProviderConfig.class)) {
            return path;
        }

        return path + "s";
    }

    SimpleHttpRequest doDelete(Class<? extends ResourceTypeRepresentation> resourceType, String id) {
        return beforeRequest(http.doDelete(baseUrl + getResourceTypePath(resourceType) + "/" + id));
    }

    SimpleHttpRequest doPut(Class<? extends ResourceTypeRepresentation> resourceType, String id) {
        return beforeRequest(http.doPut(baseUrl + getResourceTypePath(resourceType) + "/" + id))
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_SCIM_JSON)
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    SimpleHttpRequest doPatch(Class<? extends ResourceTypeRepresentation> resourceType, String id) {
        return beforeRequest(http.doPatch(baseUrl + getResourceTypePath(resourceType) + "/" + id))
                .header(HttpHeaders.CONTENT_TYPE, APPLICATION_SCIM_JSON)
                .header(HttpHeaders.ACCEPT, ACCEPT);
    }

    <T> T execute(SimpleHttpRequest request, Class<T> responseType) {
        try (SimpleHttpResponse response = execute(request)) {
            if (responseType == null || response.getStatus() == HttpStatus.SC_NO_CONTENT) {
                return null;
            }
            return response.asJson(responseType);
        } catch (IOException e) {
            throw new ScimClientException("Error executing request", e);
        }
    }

    private SimpleHttpRequest beforeRequest(SimpleHttpRequest request) {
        authorizationMethod.onBefore(http, request);
        return request;
    }

    private void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static class Builder {

        private static final String DEFAULT_API_PATH = "/scim/v2/";

        private final ScimClient client;

        private Builder(HttpClient baseUrl) {
            client = new ScimClient(baseUrl);
        }

        /**
         * The base URL of the SCIM server, e.g. "https://scim.example.com". The default API path "/scim/v2/" will be appended to this base URL.
         *
         * @param baseUrl the base URL of the SCIM server
         * @return this builder for chaining
         */
        public Builder withBaseUrl(String baseUrl) {
            client.setBaseUrl(baseUrl + DEFAULT_API_PATH);
            return this;
        }

        /**
         * The URL of the SCIM service itself, used as given (no API path is appended), e.g.
         * "https://app.example.com/scim/v2". Trailing slashes are ignored.
         *
         * @param endpoint the SCIM service URL
         * @return this builder for chaining
         */
        public Builder withEndpoint(String endpoint) {
            String url = endpoint;
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            client.setBaseUrl(url);
            return this;
        }

        /**
         * Request settings such as timeouts and redirect handling, applied to every request.
         *
         * @param requestConfig the request configuration
         * @return this builder for chaining
         */
        public Builder withRequestConfig(RequestConfig requestConfig) {
            client.setRequestConfig(requestConfig);
            return this;
        }

        /**
         * Configure the authorization method to use for requests. This method will be called before each request is sent,
         * allowing you to set the appropriate headers for authentication.
         *
         * @param method the authorization method to use for requests
         * @return this builder for chaining
         */
        public Builder withAuthorization(AuthorizationMethod method) {
            client.setAuthorizationMethod(method);
            return this;
        }

        /**
         * Builds the {@link ScimClient} instance with the configured settings. The client will be connected and ready to use after this method is called.
         *
         * @return the connected {@link ScimClient} instance
         */
        public ScimClient build() {
            return client.connect();
        }

    }

    private void setAuthorizationMethod(AuthorizationMethod method) {
        this.authorizationMethod = method;
    }

    private ScimClient connect() {
        return this;
    }

}
