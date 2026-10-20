package org.keycloak.tests.scim.provisioning.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The control and inspection API of one app of a {@link MockScimServer}: the Node mock's {@code /_admin/<key>} as
 * direct calls. Everything returned is a copy.
 */
public final class MockScimAdmin {

    private final MockScimServer server;
    private final String key;

    MockScimAdmin(MockScimServer server, String key) {
        this.server = server;
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The app's SCIM base URL. */
    public String baseUrl() {
        return server.baseUrl(key);
    }

    /** Creates a group through the app's own SCIM API, as someone inside the app would (recorded in {@link #ops()}). */
    public JsonNode createGroupInApp(String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(MockScimServer.GROUP_SCHEMA));
        body.put("displayName", displayName);
        MockScimServer.Response r = server.call("POST", "/" + key + "/scim/v2/Groups", body);
        if (r.drop() || r.status() != 201) {
            throw new IllegalStateException("create group in app → " + (r.drop() ? "connection dropped" : r.status()));
        }
        return r.body();
    }

    /** Deletes an account through the app's own SCIM API, as someone inside the app would (recorded in {@link #ops()}). */
    public void deleteUserInApp(String id) {
        MockScimServer.Response r = server.call("DELETE", "/" + key + "/scim/v2/Users/" + id, null);
        if (r.drop() || r.status() != 204) {
            throw new IllegalStateException("delete in app → " + (r.drop() ? "connection dropped" : r.status()));
        }
    }

    /**
     * Merges settings into the app's config and returns it: {@code down}, {@code status} (false or null clears it),
     * {@code retryAfter}, {@code failNext}, {@code failStatus}, {@code latencyMs}, {@code token} (null = any), and the
     * quirk flags {@code groups}, {@code requireExternalId}, {@code requireAttribute}, {@code deleteDeactivates}, {@code strictFilter}. Null values
     * are allowed (use a {@link java.util.HashMap}).
     */
    public JsonNode config(Map<String, ?> settings) {
        MockScimServer.App a = server.app(key);
        ObjectNode changes = MockScimServer.JSON.valueToTree(settings);
        synchronized (a) {
            ObjectNode next = a.config.deepCopy();
            next.setAll(changes);
            a.config = next;
            return next.deepCopy();
        }
    }

    public JsonNode config() {
        MockScimServer.App a = server.app(key);
        synchronized (a) {
            return a.config.deepCopy();
        }
    }

    /** Clears the fault-injection settings: up, no status, no failures, no latency. */
    public JsonNode heal() {
        return config(Map.of("down", false, "status", false, "failNext", 0, "latencyMs", 0));
    }

    /**
     * Every request the app received, oldest first: {@code seq, at, method, path, status} and, depending on the answer,
     * {@code type, id, userName, displayName, externalId, active, members, deactivated}. {@code path} is the request
     * target relative to the mount prefix ({@code /<key>/scim/v2/Users?filter=...}).
     */
    public List<JsonNode> ops() {
        MockScimServer.App a = server.app(key);
        synchronized (a) {
            return MockScimServer.copies(a.ops, false);
        }
    }

    public List<JsonNode> ops(Predicate<JsonNode> filter) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode o : ops()) {
            if (filter.test(o)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Requests that write: POST, PUT, PATCH, DELETE. */
    public List<JsonNode> writes() {
        return ops(o -> !o.path("method").asText().equals("GET"));
    }

    public List<JsonNode> users() {
        MockScimServer.App a = server.app(key);
        synchronized (a) {
            return MockScimServer.copies(a.users.values(), true);
        }
    }

    public JsonNode userByUserName(String userName) {
        for (JsonNode u : users()) {
            if (u.path("userName").asText().equalsIgnoreCase(userName)) {
                return u;
            }
        }
        return null;
    }

    public JsonNode user(String id) {
        for (JsonNode u : users()) {
            if (u.path("id").asText().equals(id)) {
                return u;
            }
        }
        return null;
    }

    public List<JsonNode> groups() {
        MockScimServer.App a = server.app(key);
        synchronized (a) {
            return MockScimServer.copies(a.groups.values(), true);
        }
    }

    public JsonNode groupByDisplayName(String name) {
        for (JsonNode g : groups()) {
            if (g.path("displayName").asText().equals(name)) {
                return g;
            }
        }
        return null;
    }

    /** Seeds an account made directly in the app ("already has one → linked", drift); not recorded in ops. */
    public JsonNode seedUser(Map<String, ?> user) {
        MockScimServer.App a = server.app(key);
        ObjectNode body = user == null ? MockScimServer.JSON.createObjectNode() : MockScimServer.JSON.valueToTree(user);
        ObjectNode r = MockScimServer.JSON.createObjectNode();
        r.putArray("schemas").add(MockScimServer.USER_SCHEMA);
        r.put("active", true);
        r.setAll(body);
        JsonNode id = body.get("id");
        if (id == null || id.isNull()) {
            r.put("id", UUID.randomUUID().toString());
        }
        synchronized (a) {
            MockScimServer.stamp(a, "User", r);
            a.users.put(r.get("id").asText(), r);
            return MockScimServer.view(r);
        }
    }

    /** Changes an account directly in the app (drift, reactivation); not recorded in ops. */
    public JsonNode patchUser(String id, Map<String, ?> changes) {
        MockScimServer.App a = server.app(key);
        ObjectNode body = changes == null ? MockScimServer.JSON.createObjectNode()
                : MockScimServer.JSON.valueToTree(changes);
        synchronized (a) {
            ObjectNode r = a.users.get(id);
            if (r == null) {
                throw new IllegalStateException("mock admin PATCH /users/" + id + " → 404");
            }
            r.setAll(body);
            MockScimServer.stamp(a, "User", r);
            return MockScimServer.view(r);
        }
    }

    /** {@code config, users, groups, ops}: the counts of the app's state. */
    public JsonNode summary() {
        MockScimServer.App a = server.app(key);
        synchronized (a) {
            ObjectNode out = MockScimServer.JSON.createObjectNode();
            out.set("config", a.config.deepCopy());
            out.put("users", a.users.size());
            out.put("groups", a.groups.size());
            out.put("ops", a.ops.size());
            return out;
        }
    }

    /** Forgets the app entirely. */
    public void reset() {
        server.forget(key);
    }
}
