package org.keycloak.tests.scim.provisioning.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A mock SCIM 2.0 service provider for provisioning tests: a Java port of the Node mock ({@code mock-scim-server/server.mjs}
 * of the scim-outbox extension, Apache License 2.0) on {@code com.sun.net.httpserver}.
 *
 * <p>
 * Multi-tenant by path: every first path segment is an independent "app" with its own users, groups, controls and
 * request log; {@link #baseUrl(String)} is its SCIM base URL ({@code .../<key>/scim/v2} works too). A key's quirks follow
 * its prefix:
 * <ul>
 * <li>{@code slack-*}: {@code DELETE /Users/{id}} deactivates (the account stays, {@code active: false})</li>
 * <li>{@code github-*}: {@code POST /Users} without {@code externalId} → 400</li>
 * <li>{@code zoom-*}: no Groups endpoint (404)</li>
 * <li>{@code notion-*}: only {@code userName eq} filters on {@code /Users} (others → 400)</li>
 * <li>{@code atlassian-*}: none</li>
 * </ul>
 * Any bearer token is accepted unless {@code token} is configured. {@code POST} with an existing {@code userName}
 * (case-insensitive) → 409. {@code If-Match} is honoured ({@code W/"n"}).
 *
 * <p>
 * The Node mock's HTTP admin API ({@code /_admin/<key>/...}) is replaced by {@link #admin(String)}, a direct Java API.
 *
 * <p>
 * Two ways to run it: {@link #start()} on its own server on an ephemeral port, or {@link #attach(HttpServer, String)} to
 * an existing server (the test framework's {@code @InjectHttpServer}) under a path prefix. Either way every request is
 * handled on the mock's own thread pool, so injected latency on one app never blocks another.
 */
public final class MockScimServer implements AutoCloseable {

    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    static final String LIST = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    static final String ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";
    static final String SPC_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    private static final int MAX_BODY = 1 << 20;
    private static final int MAX_OPS = 50_000;
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Pattern FILTER = Pattern.compile("^\\s*([A-Za-z.]+)\\s+eq\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");
    private static final Pattern UNESCAPE = Pattern.compile("\\\\(.)");
    private static final Pattern MEMBER_FILTER = Pattern.compile("^members\\[value eq \"([^\"]+)\"\\]$");
    private static final AtomicInteger THREADS = new AtomicInteger();

    /** key → app, in creation order. */
    private final Map<String, App> apps = new LinkedHashMap<>();
    private final HttpServer server;
    private final String prefix;
    private final boolean owned;
    private final ExecutorService executor;
    private HttpContext context;

    private MockScimServer(HttpServer server, String prefix, boolean owned) {
        this.server = server;
        this.prefix = prefix;
        this.owned = owned;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mock-scim-" + THREADS.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the mock on its own server, on an ephemeral port of 127.0.0.1. */
    public static MockScimServer start() {
        return start(0);
    }

    /** Starts the mock on its own server on 127.0.0.1:{@code port} (0 = ephemeral). */
    public static MockScimServer start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 50);
            MockScimServer mock = new MockScimServer(server, "", true);
            mock.context = server.createContext("/", mock::handle);
            server.start();
            return mock;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Serves the mock from an existing, started server under {@code prefix} (e.g. {@code /scim-mock}), replacing any
     * context already registered there. {@link #detach()} (or {@link #close()}) removes it again.
     */
    public static MockScimServer attach(HttpServer server, String prefix) {
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (!p.startsWith("/") || p.length() < 2) {
            throw new IllegalArgumentException("prefix must be a path such as /scim-mock: " + prefix);
        }
        try {
            server.removeContext(p);
        } catch (IllegalArgumentException none) {
            // nothing registered there yet
        }
        MockScimServer mock = new MockScimServer(server, p, false);
        mock.context = server.createContext(p, mock::handle);
        return mock;
    }

    /** Removes the mock's context from the server it was attached to. */
    public synchronized void detach() {
        if (context != null) {
            try {
                server.removeContext(context);
            } catch (IllegalArgumentException alreadyGone) {
                // removed by someone else
            }
            context = null;
        }
    }

    /** Stops the mock: its own server when started standalone, its context when attached. */
    @Override
    public void close() {
        detach();
        if (owned) {
            server.stop(0);
        }
        executor.shutdownNow();
    }

    /** {@code http://127.0.0.1:<port><prefix>}. */
    public URI hostUri() {
        InetSocketAddress a = server.getAddress();
        return URI.create("http://" + a.getAddress().getHostAddress() + ":" + a.getPort() + prefix);
    }

    /** The SCIM base URL of app {@code key}: {@code http://127.0.0.1:<port><prefix>/<key>}. */
    public String baseUrl(String key) {
        return hostUri() + "/" + key;
    }

    /** The SCIM base URL of app {@code key} with the {@code /scim/v2} suffix (the mock accepts both). */
    public String scimBaseUrl(String key) {
        return baseUrl(key) + "/scim/v2";
    }

    /** The control and inspection API of app {@code key} (the Node mock's {@code /_admin/<key>}). */
    public MockScimAdmin admin(String key) {
        return new MockScimAdmin(this, key);
    }

    /** The keys of every app the mock has seen. */
    public List<String> apps() {
        synchronized (apps) {
            return new ArrayList<>(apps.keySet());
        }
    }

    /** Forgets every app. */
    public void resetAll() {
        synchronized (apps) {
            apps.clear();
        }
    }

    // ---- state ----

    /** One app's state; every access holds the app's monitor. */
    static final class App {
        final String key;
        final Map<String, ObjectNode> users = new LinkedHashMap<>();
        final Map<String, ObjectNode> groups = new LinkedHashMap<>();
        final List<ObjectNode> ops = new ArrayList<>();
        long seq;
        /** Replaced, never changed, by {@link MockScimAdmin#config(Map)}, except the {@code failNext} countdown. */
        ObjectNode config;

        App(String key) {
            this.key = key;
            this.config = defaults(key);
        }
    }

    App app(String key) {
        synchronized (apps) {
            return apps.computeIfAbsent(key, App::new);
        }
    }

    void forget(String key) {
        synchronized (apps) {
            apps.remove(key);
        }
    }

    /** Quirks per app family. */
    private static ObjectNode defaults(String key) {
        String family = key.split("-", -1)[0];
        ObjectNode c = NODES.objectNode();
        c.put("family", family);
        c.putNull("token"); // null = any bearer token is accepted
        c.put("down", false); // true = connections are dropped (the app is unreachable)
        c.putNull("status"); // e.g. 429 / 500 / 401 = every SCIM call answers this
        c.putNull("retryAfter"); // seconds, sent with `status`
        c.put("failNext", 0); // the next N SCIM calls answer `failStatus`
        c.put("failStatus", 500);
        c.put("latencyMs", 0);
        c.put("groups", !family.equals("zoom")); // a vendor family without a Groups endpoint
        c.put("requireExternalId", family.equals("github")); // GitHub EMU needs externalId on create
        c.put("requireAttribute", false); // e.g. "title": POST /Users without that top-level attribute → 400
        c.put("deleteDeactivates", family.equals("slack")); // Slack: DELETE /Users/{id} deactivates
        c.put("strictFilter", family.equals("notion")); // Notion: only `userName eq` on /Users
        return c;
    }

    // ---- HTTP ----

    /** A request as the mock sees it: {@code url} is the request target relative to the mount prefix. */
    record Request(String method, String url, Function<String, String> header, BodySource body) {
    }

    interface BodySource {
        byte[] read() throws IOException;
    }

    /** A response; {@code drop} = close the connection without answering. */
    record Response(int status, Map<String, String> headers, JsonNode body, boolean drop) {
        static final Response DROP = new Response(0, Map.of(), null, true);
    }

    /** An error with an HTTP status, answered as a SCIM error. */
    static final class HttpError extends RuntimeException {
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private void handle(HttpExchange ex) {
        try {
            executor.execute(() -> serve(ex));
        } catch (RejectedExecutionException closed) {
            ex.close();
        }
    }

    private void serve(HttpExchange ex) {
        try {
            URI uri = ex.getRequestURI();
            String rel = uri.getRawPath().substring(Math.min(prefix.length(), uri.getRawPath().length()));
            if (!rel.isEmpty() && !rel.startsWith("/")) {
                // a sibling path that only shares the prefix, e.g. /scim-mockery
                write(ex, new Response(404, Map.of(), errorBody(404, "no such endpoint"), false));
                return;
            }
            if (rel.isEmpty()) {
                rel = "/";
            }
            String url = uri.getRawQuery() == null ? rel : rel + "?" + uri.getRawQuery();
            Request req = new Request(ex.getRequestMethod(), url, name -> ex.getRequestHeaders().getFirst(name),
                    () -> readLimited(ex.getRequestBody()));
            write(ex, dispatch(req));
        } catch (Throwable t) {
            ex.close();
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > MAX_BODY) {
                throw new HttpError(413, "body too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void write(HttpExchange ex, Response r) throws IOException {
        if (r.drop()) {
            // closing before the response headers are sent closes the connection: the app is unreachable
            ex.close();
            return;
        }
        byte[] bytes = r.body() == null ? null : JSON.writeValueAsBytes(r.body());
        Headers h = ex.getResponseHeaders();
        if (bytes != null) {
            h.set("Content-Type", "application/scim+json");
        }
        r.headers().forEach(h::set);
        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());
        ex.sendResponseHeaders(r.status(), bytes == null || head ? -1 : bytes.length);
        if (bytes != null && !head) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    // ---- routing ----

    Response dispatch(Request req) {
        try {
            String url = req.url();
            int q = url.indexOf('?');
            String path = q < 0 ? url : url.substring(0, q);
            Map<String, String> query = parseQuery(q < 0 ? "" : url.substring(q + 1));
            List<String> parts = new ArrayList<>();
            for (String s : path.split("/")) {
                if (!s.isEmpty()) {
                    parts.add(s);
                }
            }
            if (path.equals("/healthz")) {
                return send(200, NODES.objectNode().put("ok", true), Map.of());
            }
            if (parts.size() < 2) {
                return send(404, NODES.objectNode().put("error", "use /<app>/Users"), Map.of());
            }
            String key = parts.get(0);
            List<String> rest = parts.subList(1, parts.size());
            if (rest.size() >= 2 && rest.get(0).equals("scim") && rest.get(1).equals("v2")) {
                rest = rest.subList(2, rest.size());
            }
            return scim(req, key, rest, query);
        } catch (HttpError e) {
            return scimError(e.status, e.getMessage(), Map.of());
        } catch (RuntimeException e) {
            return scimError(500, String.valueOf(e.getMessage()), Map.of());
        }
    }

    /** {@code URLSearchParams.get}: the first value of each name. */
    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            out.putIfAbsent(name, value);
        }
        return out;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return s.replace('+', ' ');
        }
    }

    // ---- SCIM ----

    private Response scim(Request req, String key, List<String> rest, Map<String, String> query) {
        App a = app(key);
        ObjectNode c;
        double latency;
        synchronized (a) {
            c = a.config;
            if (Js.truthy(c.get("down"))) {
                return Response.DROP;
            }
            latency = Js.truthy(c.get("latencyMs")) ? Js.number(c.get("latencyMs")) : 0;
        }
        if (latency > 0) {
            try {
                Thread.sleep((long) latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Response.DROP;
            }
        }
        synchronized (a) {
            String auth = req.header().apply("Authorization");
            if (auth == null) {
                auth = "";
            }
            JsonNode token = c.get("token");
            if (!auth.startsWith("Bearer ") || (!Js.isNullish(token) && !auth.equals("Bearer " + Js.string(token)))) {
                logOp(a, req, 401, null);
                return scimError(401, "invalid token", Map.of());
            }
            double failNext = Js.number(c.get("failNext"));
            if (failNext > 0) {
                c.set("failNext", Js.numberNode(failNext - 1));
                int failStatus = (int) Js.number(c.get("failStatus"));
                logOp(a, req, failStatus, null);
                return scimError(failStatus, "injected failure", retryAfter(c));
            }
            if (Js.truthy(c.get("status"))) {
                int status = (int) Js.number(c.get("status"));
                logOp(a, req, status, null);
                boolean expired = c.get("status").isNumber() && status == 401;
                return scimError(status, expired ? "token expired" : "injected failure", retryAfter(c));
            }
        }

        String method = req.method();
        String collection = rest.isEmpty() ? null : rest.get(0);
        String id = rest.size() < 2 ? null : rest.get(1);
        JsonNode body = null;
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
            body = readBody(req);
        }

        if ("ServiceProviderConfig".equals(collection) && method.equals("GET")) {
            ObjectNode spc = NODES.objectNode();
            spc.putArray("schemas").add(SPC_SCHEMA);
            spc.putObject("patch").put("supported", true);
            spc.putObject("bulk").put("supported", false);
            spc.putObject("filter").put("supported", true).put("maxResults", 1000);
            spc.putObject("etag").put("supported", true);
            spc.putObject("changePassword").put("supported", false);
            spc.putObject("sort").put("supported", false);
            return send(200, spc, Map.of());
        }

        String type = "Users".equals(collection) ? "User" : "Groups".equals(collection) ? "Group" : null;
        if (type == null) {
            return scimError(404, "no such endpoint", Map.of());
        }
        synchronized (a) {
            if (type.equals("Group") && !Js.truthy(c.get("groups"))) {
                logOp(a, req, 404, null);
                return scimError(404, "this app does not support groups", Map.of());
            }
            Map<String, ObjectNode> store = type.equals("User") ? a.users : a.groups;
            if (id == null) {
                return collection(a, c, req, query, type, store, body);
            }
            return resource(a, c, req, type, store, id, body);
        }
    }

    private Response collection(App a, ObjectNode c, Request req, Map<String, String> query, String type,
            Map<String, ObjectNode> store, JsonNode body) {
        if (req.method().equals("GET")) {
            logOp(a, req, 200, extras("type", text(type)));
            return list(a, c, store, query, type);
        }
        if (!req.method().equals("POST")) {
            return scimError(405, "method not allowed", Map.of());
        }
        if (Js.isNullish(body) || !(body.isObject() || body.isArray())) {
            logOp(a, req, 400, extras("type", text(type)));
            return scimError(400, "body required", Map.of());
        }
        ObjectNode b = body.isObject() ? (ObjectNode) body : NODES.objectNode();
        if (type.equals("User")) {
            if (!Js.truthy(b.get("userName"))) {
                logOp(a, req, 400, extras("type", text(type)));
                return scimError(400, "userName is required", Map.of());
            }
            if (Js.truthy(c.get("requireExternalId")) && !Js.truthy(b.get("externalId"))) {
                logOp(a, req, 400, extras("type", text(type), "userName", b.get("userName")));
                return scimError(400, "externalId is required", Map.of());
            }
            JsonNode required = c.get("requireAttribute");
            if (required != null && required.isTextual() && !required.asText().isEmpty()
                    && !Js.truthy(b.get(required.asText()))) {
                logOp(a, req, 400, extras("type", text(type), "userName", b.get("userName")));
                return scimError(400, required.asText() + " is required", Map.of());
            }
            String wanted = Js.string(b.get("userName")).toLowerCase(Locale.ROOT);
            for (ObjectNode u : a.users.values()) {
                JsonNode existing = u.get("userName");
                if (Js.isNullish(existing)) {
                    throw new HttpError(500, "Cannot read properties of " + Js.string(existing)
                            + " (reading 'toLowerCase')");
                }
                if (Js.string(existing).toLowerCase(Locale.ROOT).equals(wanted)) {
                    logOp(a, req, 409, extras("userName", b.get("userName")));
                    return scimError(409, "userName already exists", Map.of());
                }
            }
        } else if (!Js.truthy(b.get("displayName"))) {
            logOp(a, req, 400, extras("type", text(type)));
            return scimError(400, "displayName is required", Map.of());
        }
        ObjectNode r = b.deepCopy();
        r.put("id", UUID.randomUUID().toString());
        JsonNode schemas = b.get("schemas");
        if (Js.isNullish(schemas)) {
            r.set("schemas", NODES.arrayNode().add(type.equals("User") ? USER_SCHEMA : GROUP_SCHEMA));
        }
        if (type.equals("User") && !r.has("active")) {
            r.put("active", true);
        }
        stamp(a, type, r);
        store.put(r.get("id").asText(), r);
        logOp(a, req, 201, extras("type", text(type), "id", r.get("id"), "userName", r.get("userName"),
                "displayName", r.get("displayName"), "externalId", r.get("externalId")));
        return send(201, view(r), Map.of("ETag", version(r), "Location", r.path("meta").path("location").asText()));
    }

    private Response resource(App a, ObjectNode c, Request req, String type, Map<String, ObjectNode> store,
            String id, JsonNode body) {
        ObjectNode r = store.get(id);
        if (r == null) {
            logOp(a, req, 404, extras("type", text(type), "id", text(id)));
            return scimError(404, type + " " + id + " not found", Map.of());
        }
        String ifMatch = req.header().apply("If-Match");
        if (ifMatch != null && !ifMatch.isEmpty() && !ifMatch.equals("*") && !ifMatch.equals(version(r))) {
            logOp(a, req, 412, extras("type", text(type), "id", text(id)));
            return scimError(412, "version mismatch", Map.of());
        }
        switch (req.method()) {
            case "GET":
                logOp(a, req, 200, extras("type", text(type), "id", text(id)));
                return send(200, view(r), Map.of("ETag", version(r)));
            case "PUT": {
                if (Js.isNullish(body) || !(body.isObject() || body.isArray())) {
                    return scimError(400, "body required", Map.of());
                }
                ObjectNode b = body.isObject() ? (ObjectNode) body : NODES.objectNode();
                ObjectNode next = b.deepCopy();
                next.put("id", id);
                setOrRemove(next, "meta", r.get("meta"));
                setOrRemove(next, "_v", r.get("_v"));
                setOrRemove(next, "schemas", Js.isNullish(b.get("schemas")) ? r.get("schemas") : b.get("schemas"));
                if (type.equals("User") && !next.has("active")) {
                    next.put("active", true);
                }
                stamp(a, type, next);
                store.put(id, next);
                logOp(a, req, 200, extras("type", text(type), "id", text(id), "userName", next.get("userName"),
                        "active", next.get("active"), "members", length(next.get("members"))));
                return send(200, view(next), Map.of("ETag", version(next)));
            }
            case "PATCH": {
                try {
                    applyPatch(r, body, type);
                } catch (HttpError e) {
                    return scimError(e.status, e.getMessage(), Map.of());
                }
                stamp(a, type, r);
                logOp(a, req, 200, extras("type", text(type), "id", text(id), "userName", r.get("userName"),
                        "active", r.get("active"), "members", length(r.get("members"))));
                return send(200, view(r), Map.of("ETag", version(r)));
            }
            case "DELETE": {
                if (type.equals("User") && Js.truthy(c.get("deleteDeactivates"))) {
                    r.put("active", false);
                    stamp(a, type, r);
                    logOp(a, req, 204, extras("type", text(type), "id", text(id), "deactivated", BooleanNode.TRUE));
                } else {
                    store.remove(id);
                    logOp(a, req, 204, extras("type", text(type), "id", text(id)));
                }
                return new Response(204, Map.of(), null, false);
            }
            default:
                return scimError(405, "method not allowed", Map.of());
        }
    }

    // ---- filters: `attr eq "value"` (userName case-insensitive, RFC 7643 §4.1.1) ----

    private Response list(App a, ObjectNode c, Map<String, ObjectNode> store, Map<String, String> query,
            String type) {
        List<ObjectNode> items = new ArrayList<>(store.values());
        String f = query.get("filter");
        if (f != null && !f.isEmpty()) {
            Matcher m = FILTER.matcher(f);
            if (!m.matches()) {
                return scimError(400, "unsupported filter", Map.of());
            }
            String attr = m.group(1);
            String value = UNESCAPE.matcher(m.group(2)).replaceAll("$1");
            if (Js.truthy(c.get("strictFilter")) && type.equals("User") && !attr.equals("userName")) {
                return scimError(400, "only userName eq filters are supported", Map.of());
            }
            List<ObjectNode> matched = new ArrayList<>();
            for (ObjectNode r : items) {
                if (matches(r, attr, value)) {
                    matched.add(r);
                }
            }
            items = matched;
        }
        // Math.max/min propagate NaN in Java as in JavaScript
        double start = Math.max(1, query.containsKey("startIndex") ? Js.number(query.get("startIndex")) : 1);
        double count = Math.max(0, Math.min(1000, query.containsKey("count") ? Js.number(query.get("count")) : 100));
        int from = sliceIndex(start - 1, items.size());
        int to = sliceIndex(start - 1 + count, items.size());
        ArrayNode resources = NODES.arrayNode();
        for (int i = from; i < to; i++) {
            resources.add(view(items.get(i)));
        }
        ObjectNode out = NODES.objectNode();
        out.putArray("schemas").add(LIST);
        out.put("totalResults", items.size());
        out.set("startIndex", Js.numberNode(start));
        out.put("itemsPerPage", resources.size());
        out.set("Resources", resources);
        return send(200, out, Map.of());
    }

    /** {@code Array.prototype.slice}'s index conversion. */
    private static int sliceIndex(double d, int len) {
        if (Double.isNaN(d)) {
            return 0;
        }
        double t = d < 0 ? Math.ceil(d) : Math.floor(d);
        if (t < 0) {
            return (int) Math.max(len + t, 0);
        }
        return (int) Math.min(t, len);
    }

    private static boolean matches(ObjectNode r, String attr, String value) {
        JsonNode v = r;
        for (String k : attr.split("\\.", -1)) {
            v = Js.isNullish(v) ? null : v.get(k);
        }
        if (Js.isNullish(v)) {
            return false;
        }
        return attr.equals("userName")
                ? Js.string(v).toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT))
                : Js.string(v).equals(value);
    }

    // ---- PATCH (RFC 7644 §3.5.2): replace/add/remove on simple, dotted and members paths ----

    private static void applyPatch(ObjectNode r, JsonNode body, String type) {
        JsonNode ops = Js.isNullish(body) ? null : body.get("Operations");
        if (ops == null || !ops.isArray()) {
            throw new HttpError(400, "Operations required");
        }
        for (JsonNode op : ops) {
            if (Js.isNullish(op)) {
                throw new HttpError(400, "Cannot read properties of null (reading 'op')");
            }
            JsonNode opName = op.get("op");
            String kind = Js.isNullish(opName) ? "" : Js.string(opName).toLowerCase(Locale.ROOT);
            JsonNode pathNode = op.get("path");
            if (!kind.equals("add") && !kind.equals("replace") && !kind.equals("remove")) {
                throw new HttpError(400, "bad op " + Js.string(opName));
            }
            JsonNode value = op.get("value");
            if (!Js.truthy(pathNode)) {
                if (kind.equals("remove")) {
                    throw new HttpError(400, "remove needs a path");
                }
                if (value != null && value.isObject()) {
                    r.setAll((ObjectNode) value.deepCopy());
                }
                continue;
            }
            String path = Js.string(pathNode);
            Matcher memberFilter = MEMBER_FILTER.matcher(path);
            boolean isMemberFilter = memberFilter.matches();
            if (type.equals("Group") && (path.equals("members") || isMemberFilter)) {
                patchMembers(r, kind, value, isMemberFilter ? memberFilter.group(1) : null);
                continue;
            }
            String[] keys = path.split("\\.", -1);
            ObjectNode o = r;
            for (int i = 0; i < keys.length - 1; i++) {
                JsonNode child = o.get(keys[i]);
                if (Js.isNullish(child)) {
                    child = NODES.objectNode();
                    o.set(keys[i], child);
                } else if (!child.isObject()) {
                    throw new HttpError(400, "Cannot create property '" + keys[i + 1] + "' on " + Js.string(child));
                }
                o = (ObjectNode) child;
            }
            String last = keys[keys.length - 1];
            if (kind.equals("remove") || value == null || value.isMissingNode()) {
                o.remove(last);
            } else if (path.equals("active") && value.isTextual()) {
                o.put(last, value.textValue().equals("true"));
            } else {
                o.set(last, value.deepCopy());
            }
        }
    }

    private static void patchMembers(ObjectNode r, String kind, JsonNode value, String filtered) {
        JsonNode current = r.get("members");
        ArrayNode members;
        if (Js.isNullish(current)) {
            members = NODES.arrayNode();
        } else if (current.isArray()) {
            members = (ArrayNode) current;
        } else if (kind.equals("replace")) {
            members = NODES.arrayNode();
        } else {
            throw new HttpError(400, "r.members.some is not a function");
        }
        r.set("members", members);
        if (kind.equals("replace")) {
            ArrayNode next = NODES.arrayNode();
            for (JsonNode m : iterable(value, "(op.value ?? []).map is not a function")) {
                next.add(member(m));
            }
            r.set("members", next);
        } else if (kind.equals("add")) {
            for (JsonNode m : iterable(value, "op.value is not iterable")) {
                JsonNode v = memberValue(m);
                boolean present = false;
                for (JsonNode x : members) {
                    if (sameValue(x.get("value"), v)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    members.add(member(m));
                }
            }
        } else if (filtered != null) {
            ArrayNode next = NODES.arrayNode();
            for (JsonNode x : members) {
                JsonNode v = x.get("value");
                if (!(v != null && v.isTextual() && v.textValue().equals(filtered))) {
                    next.add(x);
                }
            }
            r.set("members", next);
        } else if (value != null && value.isArray()) {
            ArrayNode next = NODES.arrayNode();
            for (JsonNode x : members) {
                boolean drop = false;
                for (JsonNode v : value) {
                    if (sameValue(x.get("value"), memberValue(v))) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    next.add(x);
                }
            }
            r.set("members", next);
        } else {
            r.set("members", NODES.arrayNode());
        }
    }

    private static Iterable<JsonNode> iterable(JsonNode value, String error) {
        if (Js.isNullish(value)) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new HttpError(400, error);
        }
        return value;
    }

    private static JsonNode memberValue(JsonNode m) {
        if (Js.isNullish(m)) {
            throw new HttpError(400, "Cannot read properties of " + Js.string(m) + " (reading 'value')");
        }
        return m.get("value");
    }

    /** {@code { value: m.value }}; an undefined value is left out, as JSON.stringify does. */
    private static ObjectNode member(JsonNode m) {
        JsonNode v = memberValue(m);
        ObjectNode out = NODES.objectNode();
        if (v != null) {
            out.set("value", v.deepCopy());
        }
        return out;
    }

    /** {@code ===} on JSON values: primitives by value, objects by identity (never equal across documents). */
    private static boolean sameValue(JsonNode x, JsonNode y) {
        if (x == null || y == null) {
            return x == null && y == null;
        }
        if (x.isContainerNode() || y.isContainerNode()) {
            return x == y;
        }
        if (x.isNumber() && y.isNumber()) {
            return x.doubleValue() == y.doubleValue();
        }
        return x.equals(y);
    }

    // ---- helpers ----

    private static JsonNode readBody(Request req) {
        byte[] raw;
        try {
            raw = req.body().read();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (raw.length == 0) {
            return null;
        }
        try {
            JsonNode n = JSON.readTree(raw);
            if (n == null || n.isMissingNode()) {
                throw new HttpError(400, "body is not JSON");
            }
            return n;
        } catch (IOException e) {
            throw new HttpError(400, "body is not JSON");
        }
    }

    static Response send(int status, JsonNode body, Map<String, String> headers) {
        return new Response(status, headers, body, false);
    }

    static ObjectNode errorBody(int status, String detail) {
        ObjectNode e = NODES.objectNode();
        e.putArray("schemas").add(ERROR);
        e.put("status", String.valueOf(status));
        e.put("detail", detail);
        return e;
    }

    static Response scimError(int status, String detail, Map<String, String> headers) {
        return send(status, errorBody(status, detail), headers);
    }

    private static Map<String, String> retryAfter(ObjectNode c) {
        JsonNode ra = c.get("retryAfter");
        return Js.truthy(ra) ? Map.of("Retry-After", Js.string(ra)) : Map.of();
    }

    static void stamp(App a, String type, ObjectNode r) {
        double v = Js.isNullish(r.get("_v")) ? 0 : Js.number(r.get("_v"));
        JsonNode next = Js.numberNode(v + 1);
        r.set("_v", next);
        String now = ISO.format(Instant.now());
        JsonNode oldMeta = r.get("meta");
        JsonNode created = oldMeta != null && oldMeta.isObject() ? oldMeta.get("created") : null;
        ObjectNode meta = NODES.objectNode();
        meta.put("resourceType", type);
        meta.put("location", "/" + a.key + "/" + type + "s/" + Js.string(r.get("id")));
        meta.put("version", "W/\"" + Js.numberToString(v + 1) + "\"");
        meta.set("created", Js.isNullish(created) ? NODES.textNode(now) : created);
        meta.put("lastModified", now);
        r.set("meta", meta);
    }

    static ObjectNode view(ObjectNode r) {
        ObjectNode v = r.deepCopy();
        v.remove("_v");
        return v;
    }

    private static String version(ObjectNode r) {
        return Js.string(r.path("meta").get("version"));
    }

    private static void setOrRemove(ObjectNode o, String field, JsonNode value) {
        if (value == null || value.isMissingNode()) {
            o.remove(field);
        } else {
            o.set(field, value);
        }
    }

    private static JsonNode text(String s) {
        return NODES.textNode(s);
    }

    /** {@code x?.length}: arrays and strings only. */
    private static JsonNode length(JsonNode n) {
        if (n == null) {
            return null;
        }
        if (n.isArray()) {
            return NODES.numberNode(n.size());
        }
        if (n.isTextual()) {
            return NODES.numberNode(n.textValue().length());
        }
        return null;
    }

    /** Name/value pairs for an op; a null value is {@code undefined} and left out. */
    private static Map<String, JsonNode> extras(Object... pairs) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            JsonNode v = (JsonNode) pairs[i + 1];
            if (v != null && !v.isMissingNode()) {
                out.put((String) pairs[i], v.deepCopy());
            }
        }
        return out;
    }

    private static void logOp(App a, Request req, int status, Map<String, JsonNode> extra) {
        ObjectNode op = NODES.objectNode();
        op.put("seq", ++a.seq);
        op.put("at", ISO.format(Instant.now()));
        op.put("method", req.method());
        op.put("path", req.url());
        op.put("status", status);
        if (extra != null) {
            op.setAll(extra);
        }
        a.ops.add(op);
        if (a.ops.size() > MAX_OPS) {
            a.ops.subList(0, 10_000).clear();
        }
    }

    // ---- used by MockScimAdmin ----

    /** Sends a request through the mock as a client would, without HTTP (the app's own UI, say). */
    Response call(String method, String url, Object body) {
        byte[] bytes;
        try {
            bytes = body == null ? new byte[0] : JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> headers = Map.of("authorization", "Bearer t", "content-type", "application/scim+json");
        return dispatch(new Request(method, url,
                name -> headers.get(name.toLowerCase(Locale.ROOT)), () -> bytes));
    }

    static List<JsonNode> copies(Iterable<? extends JsonNode> nodes, boolean asView) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode n : nodes) {
            out.add(asView ? view((ObjectNode) n) : n.deepCopy());
        }
        return Collections.unmodifiableList(out);
    }
}
