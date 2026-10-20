package org.keycloak.tests.scim.provisioning;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.keycloak.admin.client.Keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A small JSON admin REST client over the test framework's admin client token: enough to set realms, users, groups and
 * destinations up and to call the {@code scim-provisioning} admin API and the test-only {@code scim-provisioning-test}
 * realm resource. Answers are plain {@link Response}s, so tests can assert on status codes.
 */
public final class ProvisioningAdmin {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String base;
    private final Supplier<String> token;
    private final Runnable refresh;

    /** As the framework's (master realm) admin client. */
    public ProvisioningAdmin(String base, Keycloak adminClient) {
        this(base, () -> adminClient.tokenManager().getAccessTokenString(),
                () -> adminClient.tokenManager().invalidate(adminClient.tokenManager().getAccessTokenString()));
    }

    private ProvisioningAdmin(String base, Supplier<String> token, Runnable refresh) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.token = token;
        this.refresh = refresh;
    }

    /**
     * Gets a new token on the next call: a token issued before a realm was created does not carry the admin roles of
     * that realm.
     */
    public void refreshToken() {
        refresh.run();
    }

    /** The same API, authenticated as a user of {@code realm} (password grant through its {@code admin-cli}). */
    public ProvisioningAdmin as(String realm, String username, String password) {
        Supplier<String> userToken = () -> passwordGrant(realm, username, password);
        return new ProvisioningAdmin(base, userToken, () -> {
        });
    }

    /** An HTTP answer: status, JSON body (or null), {@code Location}, raw text. */
    public record Response(int status, JsonNode body, String location, String text) {

        public Response ok() {
            if (status >= 300) {
                throw new IllegalStateException("HTTP " + status + ": " + text);
            }
            return this;
        }

        /** The id at the end of {@code Location}. */
        public String createdId() {
            ok();
            return location.substring(location.lastIndexOf('/') + 1);
        }
    }

    // ---- generic

    public Response get(String path) {
        return send("GET", path, null, true);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body, true);
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body, true);
    }

    public Response delete(String path) {
        return send("DELETE", path, null, true);
    }

    /** A request without a token (the test-only realm resource needs none). */
    public Response anonymous(String method, String path, Object body) {
        return send(method, path, body, false);
    }

    // ---- realms

    public void createRealm(String realm) {
        post("/admin/realms", Map.of("realm", realm, "enabled", true)).ok();
        refreshToken();
    }

    /** Creates a realm from a full representation (a realm import). */
    public void importRealm(Map<String, Object> representation) {
        post("/admin/realms", representation).ok();
        refreshToken();
    }

    public void deleteRealm(String realm) {
        delete("/admin/realms/" + realm);
    }

    public String realmId(String realm) {
        return get("/admin/realms/" + realm).ok().body().get("id").asText();
    }

    /** Turns admin-event storage on for a realm. */
    public void enableAdminEvents(String realm) {
        put("/admin/realms/" + realm, Map.of("adminEventsEnabled", true, "adminEventsDetailsEnabled", true)).ok();
    }

    /** Lets user attributes not declared in the user profile be stored. */
    public void allowUnmanagedAttributes(String realm) {
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = JSON.convertValue(get("/admin/realms/" + realm + "/users/profile").ok().body(),
                Map.class);
        profile.put("unmanagedAttributePolicy", "ENABLED");
        put("/admin/realms/" + realm + "/users/profile", profile).ok();
    }

    // ---- users and groups

    public String createUser(String realm, String username, String email, String first, String last) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("username", username);
        u.put("enabled", true);
        if (email != null) {
            u.put("email", email);
        }
        if (first != null) {
            u.put("firstName", first);
        }
        if (last != null) {
            u.put("lastName", last);
        }
        return post("/admin/realms/" + realm + "/users", u).createdId();
    }

    public JsonNode user(String realm, String id) {
        return get("/admin/realms/" + realm + "/users/" + id).ok().body();
    }

    /** Merges {@code changes} into the user's representation and PUTs it. */
    public void updateUser(String realm, String id, Map<String, ?> changes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> rep = JSON.convertValue(user(realm, id), Map.class);
        rep.putAll(changes);
        put("/admin/realms/" + realm + "/users/" + id, rep).ok();
    }

    public void deleteUser(String realm, String id) {
        delete("/admin/realms/" + realm + "/users/" + id).ok();
    }

    /** Sets a permanent password. */
    public void setPassword(String realm, String userId, String password) {
        put("/admin/realms/" + realm + "/users/" + userId + "/reset-password",
                Map.of("type", "password", "value", password, "temporary", false)).ok();
    }

    /** Grants a {@code realm-management} client role (e.g. {@code view-users}). */
    public void grantRealmManagementRole(String realm, String userId, String role) {
        String cid = get("/admin/realms/" + realm + "/clients?clientId=realm-management").ok().body().get(0).get("id")
                .asText();
        JsonNode rep = get("/admin/realms/" + realm + "/clients/" + cid + "/roles/" + role).ok().body();
        post("/admin/realms/" + realm + "/users/" + userId + "/role-mappings/clients/" + cid, List.of(rep)).ok();
    }

    public String createGroup(String realm, String name) {
        return post("/admin/realms/" + realm + "/groups", Map.of("name", name)).createdId();
    }

    public String createSubGroup(String realm, String parentId, String name) {
        Response r = post("/admin/realms/" + realm + "/groups/" + parentId + "/children", Map.of("name", name)).ok();
        if (r.location() != null) {
            return r.location().substring(r.location().lastIndexOf('/') + 1);
        }
        return r.body().get("id").asText();
    }

    public void renameGroup(String realm, String id, String name) {
        put("/admin/realms/" + realm + "/groups/" + id, Map.of("name", name)).ok();
    }

    public void deleteGroup(String realm, String id) {
        delete("/admin/realms/" + realm + "/groups/" + id).ok();
    }

    public void joinGroup(String realm, String userId, String groupId) {
        put("/admin/realms/" + realm + "/users/" + userId + "/groups/" + groupId, Map.of()).ok();
    }

    public void leaveGroup(String realm, String userId, String groupId) {
        delete("/admin/realms/" + realm + "/users/" + userId + "/groups/" + groupId).ok();
    }

    // ---- destinations

    /** Creates a {@code scim-provisioning} destination; config values are lists of strings, as in kcadm. */
    public Response createDestination(String realm, String name, Map<String, List<String>> config) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("providerId", "scim-provisioning");
        c.put("providerType", "org.keycloak.storage.UserStorageProvider");
        c.put("config", config);
        return post("/admin/realms/" + realm + "/components", c);
    }

    public JsonNode component(String realm, String id) {
        return get("/admin/realms/" + realm + "/components/" + id).ok().body();
    }

    /** Merges config values into a component and PUTs it. */
    public Response updateComponentConfig(String realm, String id, Map<String, List<String>> changes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> rep = JSON.convertValue(component(realm, id), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) rep.get("config");
        cfg.putAll(changes);
        return put("/admin/realms/" + realm + "/components/" + id, rep);
    }

    public void deleteComponent(String realm, String id) {
        delete("/admin/realms/" + realm + "/components/" + id).ok();
    }

    public static String q(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ---- plumbing

    private String passwordGrant(String realm, String username, String password) {
        String form = "grant_type=password&client_id=admin-cli&username=" + q(username) + "&password=" + q(password);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("token for " + username + ": HTTP " + r.statusCode());
            }
            return JSON.readTree(r.body()).get("access_token").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Response send(String method, String path, Object body, boolean auth) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
            if (auth) {
                b.header("Authorization", "Bearer " + token.get());
            }
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = null;
            if (r.body() != null && !r.body().isBlank()) {
                try {
                    json = JSON.readTree(r.body());
                } catch (IOException notJson) {
                    // not JSON
                }
            }
            return new Response(r.statusCode(), json, r.headers().firstValue("Location").orElse(null), r.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
