package org.keycloak.tests.scim.provisioning.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The mock's SCIM behaviour and its controls: a port of the Node mock's {@code test/server.test.mjs}, and more. */
public class MockScimServerTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static MockScimServer mock;

    @BeforeAll
    public static void start() {
        mock = MockScimServer.start();
    }

    @AfterAll
    public static void stop() {
        mock.close();
    }

    private static HttpResponse<String> call(String method, String url, Object body) throws Exception {
        return call(method, url, body, Map.of());
    }

    private static HttpResponse<String> call(String method, String url, Object body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer t").header("Content-Type", "application/scim+json");
        headers.forEach(b::setHeader);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(MockScimServer.JSON.writeValueAsBytes(body)));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String app(String key) {
        return mock.baseUrl(key);
    }

    private static JsonNode json(HttpResponse<String> r) throws IOException {
        return MockScimServer.JSON.readTree(r.body());
    }

    private static String filter(String f) {
        return "?filter=" + URLEncoder.encode(f, StandardCharsets.UTF_8);
    }

    @Test
    public void usersCreateFilterPatchPutDeleteAnd409OnDuplicateUserName() throws Exception {
        String base = app("atlassian-t1");
        HttpResponse<String> r = call("POST", base + "/Users",
                Map.of("userName", "ana@acme.test", "externalId", "k1", "active", true));
        assertEquals(201, r.statusCode());
        JsonNode u = json(r);
        assertEquals("W/\"1\"", r.headers().firstValue("ETag").orElse(null));
        assertEquals("/atlassian-t1/Users/" + u.get("id").asText(), r.headers().firstValue("Location").orElse(null));
        assertEquals(409, call("POST", base + "/Users", Map.of("userName", "ANA@acme.test")).statusCode());
        JsonNode found = json(call("GET", base + "/Users" + filter("userName eq \"Ana@Acme.test\""), null));
        assertEquals(1, found.get("totalResults").asInt());
        HttpResponse<String> p = call("PATCH", base + "/Users/" + u.get("id").asText(),
                Map.of("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"), "Operations",
                        List.of(Map.of("op", "replace", "path", "active", "value", false))));
        assertEquals(false, json(p).get("active").booleanValue());
        HttpResponse<String> put = call("PUT", base + "/Users/" + u.get("id").asText(),
                Map.of("userName", "ana@acme.test", "active", true, "name", Map.of("givenName", "Ana")));
        assertEquals("Ana", json(put).get("name").get("givenName").asText());
        assertEquals(204, call("DELETE", base + "/Users/" + u.get("id").asText(), null).statusCode());
        assertEquals(404, call("GET", base + "/Users/" + u.get("id").asText(), null).statusCode());
    }

    @Test
    public void quirksZoomNoGroupsGithubNeedsExternalIdSlackDeleteDeactivatesNotionFilters() throws Exception {
        assertEquals(404, call("GET", app("zoom-t1") + "/Groups", null).statusCode());
        assertEquals(400, call("POST", app("github-t1") + "/Users", Map.of("userName", "ana")).statusCode());
        JsonNode s = json(call("POST", app("slack-t1") + "/Users", Map.of("userName", "ana@acme.test")));
        assertEquals(204, call("DELETE", app("slack-t1") + "/Users/" + s.get("id").asText(), null).statusCode());
        assertEquals(false,
                json(call("GET", app("slack-t1") + "/Users/" + s.get("id").asText(), null)).get("active").booleanValue());
        assertEquals(400, call("GET", app("notion-t1") + "/Users" + filter("emails eq \"x\""), null).statusCode());
    }

    @Test
    public void groupsMembersReplaceAddRemove() throws Exception {
        JsonNode g = json(call("POST", app("notion-t2") + "/Groups",
                Map.of("displayName", "Engineering", "members", List.of(Map.of("value", "a")))));
        String url = app("notion-t2") + "/Groups/" + g.get("id").asText();
        call("PATCH", url, Map.of("Operations", List.of(Map.of("op", "add", "path", "members", "value",
                List.of(Map.of("value", "b"))))));
        call("PATCH", url, Map.of("Operations", List.of(Map.of("op", "remove", "path", "members[value eq \"a\"]"))));
        JsonNode now = json(call("GET", url, null));
        assertEquals(MockScimServer.JSON.readTree("[{\"value\":\"b\"}]"), now.get("members"));
        call("PATCH", url, Map.of("Operations", List.of(Map.of("op", "replace", "path", "members", "value",
                List.of(Map.of("value", "c"), Map.of("value", "d"))))));
        call("PATCH", url, Map.of("Operations", List.of(Map.of("op", "remove", "path", "members", "value",
                List.of(Map.of("value", "c"))))));
        assertEquals(MockScimServer.JSON.readTree("[{\"value\":\"d\"}]"), json(call("GET", url, null)).get("members"));
    }

    @Test
    public void controlsDownStatusFailNextTokenAndOpsAreRecorded() throws Exception {
        MockScimAdmin admin = mock.admin("slack-t3");
        String users = app("slack-t3") + "/Users";
        admin.config(Map.of("status", 429, "retryAfter", 7));
        HttpResponse<String> r = call("GET", users, null);
        assertEquals(429, r.statusCode());
        assertEquals("7", r.headers().firstValue("retry-after").orElse(null));
        Map<String, Object> down = new HashMap<>();
        down.put("status", null);
        down.put("down", true);
        admin.config(down);
        assertThrows(IOException.class, () -> call("GET", users, null));
        admin.config(Map.of("down", false, "token", "right"));
        assertEquals(401, call("GET", users, null).statusCode());
        Map<String, Object> fail = new HashMap<>();
        fail.put("token", null);
        fail.put("failNext", 1);
        fail.put("failStatus", 503);
        admin.config(fail);
        assertEquals(503, call("GET", users, null).statusCode());
        assertEquals(200, call("GET", users, null).statusCode());
        List<JsonNode> ops = admin.ops();
        assertThat(ops.size(), greaterThanOrEqualTo(4));
        assertEquals("/slack-t3/Users", ops.get(0).get("path").asText());
        assertEquals(429, ops.get(0).get("status").asInt());
    }

    @Test
    public void statusFalseClears401SaysTokenExpiredAndHealResets() throws Exception {
        MockScimAdmin admin = mock.admin("atlassian-t4");
        admin.config(Map.of("status", 401));
        HttpResponse<String> r = call("GET", admin.baseUrl() + "/Users", null);
        assertEquals(401, r.statusCode());
        assertEquals("token expired", json(r).get("detail").asText());
        assertEquals("401", json(r).get("status").asText());
        admin.config(Map.of("status", false));
        assertEquals(200, call("GET", admin.baseUrl() + "/Users", null).statusCode());
        admin.config(Map.of("down", true, "failNext", 3, "latencyMs", 5));
        JsonNode healed = admin.heal();
        assertEquals(false, healed.get("down").booleanValue());
        assertEquals(0, healed.get("failNext").asInt());
        assertEquals(200, call("GET", admin.baseUrl() + "/scim/v2/Users", null).statusCode());
    }

    @Test
    public void pagingFiltersAndIfMatch() throws Exception {
        String base = app("atlassian-t5") + "/scim/v2";
        for (int i = 0; i < 5; i++) {
            assertEquals(201, call("POST", base + "/Users", Map.of("userName", "u" + i, "externalId", "e" + i))
                    .statusCode());
        }
        JsonNode page = json(call("GET", base + "/Users?startIndex=2&count=2", null));
        assertEquals(5, page.get("totalResults").asInt());
        assertEquals(2, page.get("startIndex").asInt());
        assertEquals(2, page.get("itemsPerPage").asInt());
        assertEquals("u1", page.get("Resources").get(0).get("userName").asText());
        assertEquals(1, json(call("GET", base + "/Users" + filter("externalId eq \"e3\""), null))
                .get("totalResults").asInt());
        assertEquals(400, call("GET", base + "/Users" + filter("userName co \"u\""), null).statusCode());

        JsonNode u = mock.admin("atlassian-t5").userByUserName("U2");
        assertThat(u, notNullValue());
        String url = base + "/Users/" + u.get("id").asText();
        Map<String, Object> patch = Map.of("Operations",
                List.of(Map.of("op", "replace", "path", "name.givenName", "value", "Two")));
        assertEquals(412, call("PATCH", url, patch, Map.of("If-Match", "W/\"9\"")).statusCode());
        HttpResponse<String> ok = call("PATCH", url, patch, Map.of("If-Match", "W/\"1\""));
        assertEquals(200, ok.statusCode());
        assertEquals("W/\"2\"", ok.headers().firstValue("ETag").orElse(null));
        assertEquals("Two", json(ok).get("name").get("givenName").asText());
        assertThat(json(ok).has("_v"), is(false));
        assertEquals(404, call("GET", app("atlassian-t5") + "/Nope", null).statusCode());
        assertEquals(true, json(call("GET", base + "/ServiceProviderConfig", null)).get("patch").get("supported")
                .booleanValue());
    }

    @Test
    public void adminApiSeedPatchWritesCreateGroupDeleteUserReset() throws Exception {
        MockScimAdmin admin = mock.admin(Keys.unique("atlassian"));
        JsonNode seeded = admin.seedUser(Map.of("userName", "already@acme.test"));
        assertEquals(true, seeded.get("active").booleanValue());
        assertEquals(409, call("POST", admin.baseUrl() + "/Users", Map.of("userName", "Already@acme.test"))
                .statusCode());
        JsonNode patched = admin.patchUser(seeded.get("id").asText(), Map.of("active", false));
        assertEquals("W/\"2\"", patched.get("meta").get("version").asText());
        JsonNode group = admin.createGroupInApp("Sales");
        assertThat(admin.groupByDisplayName("Sales"), notNullValue());
        admin.deleteUserInApp(seeded.get("id").asText());
        assertThat(admin.userByUserName("already@acme.test"), nullValue());
        List<JsonNode> writes = admin.writes();
        assertEquals(3, writes.size());
        assertEquals(409, writes.get(0).get("status").asInt());
        assertEquals("/" + admin.key() + "/scim/v2/Groups", writes.get(1).get("path").asText());
        assertEquals(group.get("id").asText(), writes.get(1).get("id").asText());
        assertEquals("DELETE", writes.get(2).get("method").asText());
        assertThrows(IllegalStateException.class, () -> admin.patchUser("missing", Map.of("active", true)));
        admin.reset();
        assertEquals(0, admin.ops().size());
        assertEquals(0, admin.groups().size());
    }

    @Test
    public void latencyOnOneAppDoesNotBlockAnother() throws Exception {
        assertLatencyIsolated(mock, "atlassian-slow1", "atlassian-fast1");
    }

    @Test
    public void attachedToAServerLikeTheInjectedOne() throws Exception {
        // the test framework's HttpServerSupplier: no executor, i.e. handlers run on the dispatcher thread
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 10);
        server.start();
        try {
            MockScimServer attached = MockScimServer.attach(server, "/scim-mock");
            int port = server.getAddress().getPort();
            assertEquals("http://127.0.0.1:" + port + "/scim-mock/atlassian-a1", attached.baseUrl("atlassian-a1"));
            HttpResponse<String> r = call("POST", attached.baseUrl("atlassian-a1") + "/Users",
                    Map.of("userName", "ana"));
            assertEquals(201, r.statusCode());
            assertEquals("/atlassian-a1/Users", attached.admin("atlassian-a1").ops().get(0).get("path").asText());
            assertLatencyIsolated(attached, "atlassian-slow2", "atlassian-fast2");
            // attaching again replaces the context (a fresh mock per test class)
            MockScimServer again = MockScimServer.attach(server, "/scim-mock");
            assertEquals(201, call("POST", again.baseUrl("atlassian-a1") + "/Users", Map.of("userName", "ana"))
                    .statusCode());
            again.detach();
            assertEquals(404, call("GET", again.baseUrl("atlassian-a1") + "/Users", null).statusCode());
            attached.close();
            again.close();
        } finally {
            server.stop(0);
        }
    }

    private static void assertLatencyIsolated(MockScimServer m, String slow, String fast) throws Exception {
        m.admin(slow).config(Map.of("latencyMs", 1500));
        HttpRequest slowRequest = HttpRequest.newBuilder(URI.create(m.baseUrl(slow) + "/Users"))
                .header("Authorization", "Bearer t").timeout(Duration.ofSeconds(10)).build();
        long t0 = System.nanoTime();
        CompletableFuture<HttpResponse<String>> pending = HTTP.sendAsync(slowRequest,
                HttpResponse.BodyHandlers.ofString());
        Thread.sleep(100);
        long t1 = System.nanoTime();
        assertEquals(200, call("GET", m.baseUrl(fast) + "/Users", null).statusCode());
        assertThat((System.nanoTime() - t1) / 1_000_000, lessThan(1000L));
        assertEquals(200, pending.get().statusCode());
        assertThat((System.nanoTime() - t0) / 1_000_000, greaterThanOrEqualTo(1500L));
        assertThat(m.admin(slow).ops().get(0).get("path").asText(), containsString("/Users"));
    }
}
