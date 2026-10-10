package org.keycloak.scim.provisioning.client;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.keycloak.scim.client.ScimClient;
import org.keycloak.scim.client.ScimClientException;
import org.keycloak.scim.client.authorization.BearerToken;
import org.keycloak.scim.protocol.request.PatchRequest;
import org.keycloak.scim.protocol.response.ErrorResponse;
import org.keycloak.scim.protocol.response.ListResponse;
import org.keycloak.scim.resource.ResourceTypeRepresentation;
import org.keycloak.scim.resource.group.Group;
import org.keycloak.scim.resource.user.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;

/**
 * The SCIM 2.0 calls the delivery engine needs, over Keycloak's {@link ScimClient}, with every
 * failure classified into an {@link ErrorClass}.
 *
 * <p>Requests carry {@code Authorization: Bearer} from the token supplier, read on every request;
 * the token is never part of an exception or a log line. Redirects are never followed: a 3xx is
 * {@link ErrorClass#MISCONFIGURED}.
 */
public final class ScimEndpoint {

    private final ScimClient client;

    public ScimEndpoint(ScimClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * @param http the HTTP client to send with; it carries Keycloak's proxy and trust settings
     * @param baseUrl the SCIM service URL, e.g. {@code https://app.example.com/scim/v2}
     * @param token the bearer token, read on every request
     * @param requestTimeout connect, socket and pool timeout of every request
     */
    public static ScimEndpoint create(HttpClient http, String baseUrl, Supplier<String> token, Duration requestTimeout) {
        int millis = (int) Math.min(Integer.MAX_VALUE, requestTimeout.toMillis());
        RequestConfig config = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(millis)
                .setSocketTimeout(millis)
                .setConnectionRequestTimeout(millis)
                .build();
        return new ScimEndpoint(ScimClient.create(http)
                .withEndpoint(baseUrl)
                .withAuthorization(new BearerToken(token))
                .withRequestConfig(config)
                .build());
    }

    // ---- users ----

    /** The first user whose {@code userName} equals the value, ignoring case (RFC 7643 §4.1.1). */
    public Optional<User> findUserByUserName(String userName) throws ScimException {
        List<User> found = call(true, () -> client.users().list("userName eq " + quote(userName), null, null)).getResources();
        for (User u : found) {
            if (u.getUserName() != null && u.getUserName().equalsIgnoreCase(userName)) {
                return Optional.of(requireId(u));
            }
        }
        return Optional.empty();
    }

    /** The user, or empty when the destination answers 404. */
    public Optional<User> getUser(String id) throws ScimException {
        return Optional.ofNullable(call(false, () -> client.users().get(segment(id))));
    }

    /** {@code POST /Users}; the response must carry an {@code id}. */
    public User createUser(User user) throws ScimException {
        return requireId(call(true, () -> client.users().create(user)));
    }

    /** {@code PUT /Users/{id}}; a 404 is {@link ErrorClass#REJECTED} with status 404. */
    public void replaceUser(String id, User user) throws ScimException {
        call(false, () -> client.users().update(segment(id), user));
    }

    public void patchUser(String id, PatchRequest patch) throws ScimException {
        call(false, () -> {
            client.users().patch(segment(id), patch);
            return null;
        });
    }

    /** {@code DELETE /Users/{id}}; {@code false} when it was already gone (404). */
    public boolean deleteUser(String id) throws ScimException {
        try {
            call(false, () -> {
                client.users().delete(segment(id));
                return null;
            });
            return true;
        } catch (ScimException e) {
            if (e.status() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** One page of {@code GET /Users}; {@code startIndex} is 1-based (RFC 7644 §3.4.2.4). */
    public ListPage listUsers(int startIndex, int count) throws ScimException {
        ListResponse<User> page = call(true, () -> client.users().list(null, startIndex, count));
        int start = page.getStartIndex() == null ? startIndex : page.getStartIndex();
        return new ListPage(page.getResources() == null ? List.of() : page.getResources(), page.getTotalResults(), start);
    }

    // ---- groups ----

    public Optional<Group> findGroupByDisplayName(String displayName) throws ScimException {
        List<Group> found = call(true, () -> client.groups().list("displayName eq " + quote(displayName), null, null)).getResources();
        for (Group g : found) {
            if (g.getDisplayName() != null && g.getDisplayName().equalsIgnoreCase(displayName)) {
                return Optional.of(requireId(g));
            }
        }
        return Optional.empty();
    }

    public Group createGroup(Group group) throws ScimException {
        return requireId(call(true, () -> client.groups().create(group)));
    }

    public void replaceGroup(String id, Group group) throws ScimException {
        call(false, () -> client.groups().update(segment(id), group));
    }

    public void patchGroup(String id, PatchRequest patch) throws ScimException {
        call(false, () -> {
            client.groups().patch(segment(id), patch);
            return null;
        });
    }

    // ---- service ----

    /** The connection check: an authenticated {@code GET /Users?count=1} that must parse. */
    public void check() throws ScimException {
        call(true, () -> client.users().list(null, null, 1));
    }

    // ---- plumbing ----

    private interface Call<T> {
        T run();
    }

    /**
     * @param collection whether the request addresses a collection ({@code /Users}) rather than a
     *     resource ({@code /Users/{id}}): a 404 on a collection means the endpoint is wrong
     */
    private static <T> T call(boolean collection, Call<T> call) throws ScimException {
        try {
            return call.run();
        } catch (ScimClientException e) {
            throw classify(e, collection);
        }
    }

    static ScimException classify(ScimClientException e, boolean collection) {
        int status = e.getStatus();
        if (status > 0) {
            ErrorClass c = ErrorClass.forStatus(status, collection);
            ErrorResponse error = e.getError();
            String detail = status >= 300 && status < 400 ? "redirect refused" : error == null ? null : error.getDetail();
            String scimType = error == null ? null : error.getScimType();
            Duration retryAfter = RetryAfter.parse(e.getRetryAfter()).orElse(null);
            return new ScimException(c, status, detail, scimType, retryAfter);
        }
        Throwable cause = rootIo(e);
        if (cause instanceof JsonProcessingException || cause == null) {
            return new ScimException(ErrorClass.PROTOCOL, 0, "the response could not be read", null, null);
        }
        return new ScimException(ErrorClass.UNREACHABLE, 0, cause.getClass().getSimpleName(), null, null);
    }

    private static Throwable rootIo(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof JsonProcessingException) {
                return c;
            }
            if (c instanceof IOException) {
                return c;
            }
        }
        return null;
    }

    private static <R extends ResourceTypeRepresentation> R requireId(R resource) throws ScimException {
        if (resource == null || resource.getId() == null || resource.getId().isEmpty()) {
            throw new ScimException(ErrorClass.PROTOCOL, "the destination returned a resource without an id");
        }
        return resource;
    }

    /** A JSON string literal, as RFC 7644 §3.4.2.2 compValue. */
    static String quote(String value) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                b.append('\\');
            }
            b.append(c);
        }
        return b.append('"').toString();
    }

    private static String segment(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
