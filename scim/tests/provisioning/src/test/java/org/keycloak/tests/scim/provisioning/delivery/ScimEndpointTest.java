package org.keycloak.tests.scim.provisioning.delivery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ListPage;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.resource.user.User;
import org.keycloak.tests.scim.provisioning.mock.Keys;
import org.keycloak.tests.scim.provisioning.mock.MockScimAdmin;
import org.keycloak.tests.scim.provisioning.mock.MockScimServer;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ScimEndpoint} over Keycloak's SCIM client against the mock: error classification and the wire format. */
public class ScimEndpointTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static MockScimServer mock;
    private static CloseableHttpClient http;

    @BeforeAll
    public static void start() {
        mock = MockScimServer.start();
        http = HttpClientBuilder.create().build();
    }

    @AfterAll
    public static void stop() throws IOException {
        http.close();
        mock.close();
    }

    private static ScimEndpoint client(String key) {
        return client(key, "t");
    }

    private static ScimEndpoint client(String key, String token) {
        return ScimEndpoint.create(http, mock.baseUrl(key), () -> token, TIMEOUT);
    }

    private static User user(String userName) {
        User u = new User();
        u.setUserName(userName);
        u.setExternalId("ext-" + userName);
        u.setActive(true);
        return u;
    }

    private static ErrorClass classOf(ThrowingCall c) {
        return assertThrows(ScimException.class, c::run).errorClass();
    }

    interface ThrowingCall {
        void run() throws ScimException;
    }

    @Test
    public void rateLimitedCarriesRetryAfter() {
        String key = Keys.unique("atlassian");
        mock.admin(key).config(Map.of("status", 429, "retryAfter", 120));
        ScimException e = assertThrows(ScimException.class, () -> client(key).createUser(user("a")));
        assertEquals(ErrorClass.RATE_LIMITED, e.errorClass());
        assertEquals(429, e.status());
        assertEquals(Optional.of(Duration.ofSeconds(120)), e.retryAfter());
        assertTrue(e.isTransient());
    }

    @Test
    public void unauthorisedIsAuthRejected() throws Exception {
        String key = Keys.unique("atlassian");
        mock.admin(key).config(Map.of("token", "secret"));
        assertEquals(ErrorClass.AUTH_REJECTED, classOf(() -> client(key, "wrong").check()));
        client(key, "secret").check(); // the right token passes the connection check
    }

    @Test
    public void forbiddenIsAuthRejected() {
        String key = Keys.unique("atlassian");
        mock.admin(key).config(Map.of("status", 403));
        assertEquals(ErrorClass.AUTH_REJECTED, classOf(() -> client(key).getUser("x")));
    }

    @Test
    public void notFoundOnACollectionIsMisconfiguredButOnAResourceIsRejected() throws Exception {
        String key = Keys.unique("atlassian");
        // a base URL with a wrong path: every collection is a 404
        ScimEndpoint wrong = ScimEndpoint.create(http, mock.hostUri() + "/" + key + "/wrong", () -> "t", TIMEOUT);
        assertEquals(ErrorClass.MISCONFIGURED, classOf(() -> wrong.createUser(user("a"))));
        assertEquals(ErrorClass.MISCONFIGURED, classOf(() -> wrong.findUserByUserName("a")));

        ScimEndpoint c = client(key);
        assertTrue(c.getUser("no-such-id").isEmpty());
        ScimException e = assertThrows(ScimException.class, () -> c.replaceUser("no-such-id", user("a")));
        assertEquals(ErrorClass.REJECTED, e.errorClass());
        assertEquals(404, e.status());
        assertFalse(c.deleteUser("no-such-id"));
    }

    @Test
    public void duplicateUserNameIsConflict() throws Exception {
        String key = Keys.unique("atlassian");
        ScimEndpoint c = client(key);
        c.createUser(user("dup"));
        assertEquals(ErrorClass.CONFLICT, classOf(() -> c.createUser(user("DUP"))));
    }

    @Test
    public void badRequestIsRejectedWithDetail() {
        String key = Keys.unique("github");
        User noExternalId = user("x");
        noExternalId.setExternalId(null);
        ScimException e = assertThrows(ScimException.class, () -> client(key).createUser(noExternalId));
        assertEquals(ErrorClass.REJECTED, e.errorClass());
        assertFalse(e.isTransient());
        assertEquals("externalId is required", e.detail());
    }

    @Test
    public void serverErrorIsTransient() {
        String key = Keys.unique("atlassian");
        mock.admin(key).config(Map.of("status", 500));
        assertEquals(ErrorClass.SERVER_ERROR, classOf(() -> client(key).check()));
    }

    @Test
    public void downIsUnreachable() {
        String key = Keys.unique("atlassian");
        MockScimAdmin admin = mock.admin(key);
        admin.config(Map.of("down", true));
        try {
            assertEquals(ErrorClass.UNREACHABLE, classOf(() -> client(key).check()));
        } finally {
            admin.heal();
        }
    }

    @Test
    public void findFiltersByUserNameIgnoringCase() throws Exception {
        String key = Keys.unique("notion");
        ScimEndpoint c = client(key);
        String id = c.createUser(user("Mixed.Case@example.com")).getId();
        assertEquals(id, c.findUserByUserName("mixed.case@example.com").orElseThrow().getId());
        assertTrue(c.findUserByUserName("nobody").isEmpty());
        // a quote in the value is escaped inside the filter's JSON string
        assertTrue(c.findUserByUserName("we\"ird").isEmpty());
    }

    @Test
    public void listUsersPages() throws Exception {
        String key = Keys.unique("atlassian");
        ScimEndpoint c = client(key);
        for (int i = 0; i < 5; i++) {
            c.createUser(user("p" + i));
        }
        ListPage first = c.listUsers(1, 2);
        assertThat(first.resources(), hasSize(2));
        assertEquals(5, first.totalResults());
        assertThat(c.listUsers(5, 2).resources(), hasSize(1));
    }

    @Test
    public void redirectIsRefusedAndNotFollowed() throws Exception {
        AtomicInteger followed = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/scim/v2/Users", x -> {
            x.getResponseHeaders().add("Location", "http://127.0.0.1:" + x.getLocalAddress().getPort() + "/elsewhere");
            x.sendResponseHeaders(302, -1);
            x.close();
        });
        server.createContext("/elsewhere", x -> {
            followed.incrementAndGet();
            x.sendResponseHeaders(200, -1);
            x.close();
        });
        server.start();
        try {
            ScimEndpoint c = ScimEndpoint.create(http,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/scim/v2", () -> "t", TIMEOUT);
            ScimException e = assertThrows(ScimException.class, c::check);
            assertEquals(ErrorClass.MISCONFIGURED, e.errorClass());
            assertEquals(302, e.status());
            assertEquals(0, followed.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void detailIsTruncatedTo300() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String longDetail = "d".repeat(2000);
        server.createContext("/scim/v2/Users", x -> {
            byte[] b = ("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"],\"status\":\"400\","
                    + "\"scimType\":\"invalidValue\",\"detail\":\"" + longDetail + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().add("Content-Type", "application/scim+json");
            x.sendResponseHeaders(400, b.length);
            x.getResponseBody().write(b);
            x.close();
        });
        server.start();
        try {
            ScimEndpoint c = ScimEndpoint.create(http,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/scim/v2", () -> "secret-token", TIMEOUT);
            ScimException e = assertThrows(ScimException.class, () -> c.createUser(user("a")));
            assertEquals(300, e.detail().length());
            assertEquals("invalidValue", e.scimType());
            assertThat(e.getMessage(), not(containsString("secret-token")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void requestsCarryScimMediaTypesAndBearerToken() throws Exception {
        String key = Keys.unique("atlassian");
        RecordingHttpClient rec = new RecordingHttpClient(HttpClientBuilder.create().build());
        try (rec) {
            ScimEndpoint c = ScimEndpoint.create(rec, mock.baseUrl(key), () -> "tok", TIMEOUT);
            c.createUser(user("h"));
            c.check();
            RecordingHttpClient.Request post = rec.requests.get(0);
            assertEquals("POST", post.method());
            assertThat(post.header("Content-Type"), containsString("application/scim+json"));
            assertEquals("Bearer tok", post.header("Authorization"));
            // Keycloak's SCIM client sends Accept on reads only (the extension sent it on every request)
            RecordingHttpClient.Request get = rec.requests.get(1);
            assertEquals("GET", get.method());
            assertEquals("application/scim+json, application/json", get.header("Accept"));
            assertEquals("Bearer tok", get.header("Authorization"));
        }
    }

    /** The token supplier is read on every request, so a rotated token takes effect without rebuilding. */
    @Test
    public void tokenIsReadOnEveryRequest() throws Exception {
        String key = Keys.unique("atlassian");
        AtomicInteger n = new AtomicInteger();
        RecordingHttpClient rec = new RecordingHttpClient(HttpClientBuilder.create().build());
        try (rec) {
            ScimEndpoint c = ScimEndpoint.create(rec, mock.baseUrl(key), () -> "tok-" + n.incrementAndGet(), TIMEOUT);
            c.check();
            c.check();
            assertEquals("Bearer tok-1", rec.requests.get(0).header("Authorization"));
            assertEquals("Bearer tok-2", rec.requests.get(1).header("Authorization"));
        }
    }
}
