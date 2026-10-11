package org.keycloak.tests.scim.provisioning;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.testframework.annotations.InjectAdminClient;
import org.keycloak.testframework.annotations.InjectHttpServer;
import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.server.KeycloakUrls;
import org.keycloak.tests.scim.provisioning.mock.Keys;
import org.keycloak.tests.scim.provisioning.mock.MockScimAdmin;
import org.keycloak.tests.scim.provisioning.mock.MockScimServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.awaitility.Awaitility.await;

/**
 * Base of the provisioning integration tests: a fresh realm with a unique name per test method, the mock SCIM server
 * attached to the framework's HTTP server, and destination and inspection helpers. Every test gets its own mock app key,
 * so tests never see each other's accounts.
 */
@ExtendWith(ServerLog.class)
public abstract class AbstractScimProvisioningTest {

    protected static final Duration WAIT = Duration.ofSeconds(30);
    protected static final String TEST_RESOURCE = "scim-provisioning-test";

    @InjectRealm(lifecycle = LifeCycle.METHOD, config = UniqueRealm.class)
    protected ManagedRealm managedRealm;

    @InjectAdminClient
    protected Keycloak adminClient;

    @InjectKeycloakUrls
    protected KeycloakUrls keycloakUrls;

    @InjectHttpServer
    protected HttpServer httpServer;

    protected MockScimServer mockServer;
    protected ProvisioningAdmin admin;
    protected String realm;

    /** A realm name that differs per test, so realm ids (and the rows keyed by them) never repeat. */
    public static class UniqueRealm implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.name("r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
    }

    @BeforeEach
    void attachMockAndAdmin() {
        mockServer = MockScimServer.attach(httpServer, "/scim-mock");
        admin = new ProvisioningAdmin(keycloakUrls.getBase(), adminClient);
        // the realm was created after our token was issued
        admin.refreshToken();
        realm = managedRealm.getName();
    }

    @AfterEach
    void detachMock() {
        if (mockServer != null) {
            mockServer.close();
        }
    }

    /** A destination pointing at a fresh mock app. */
    protected final class Dest {
        final String id;
        final String key;
        final String realmName;
        final MockScimAdmin mock;

        Dest(String realmName, String id, String key) {
            this.realmName = realmName;
            this.id = id;
            this.key = key;
            this.mock = mockServer.admin(key);
        }

        JsonNode rows(String subject) {
            return admin.anonymous("GET", "/realms/" + realmName + "/" + TEST_RESOURCE + "/rows?destination=" + id
                    + "&subject=" + ProvisioningAdmin.q(subject), null).ok().body();
        }

        /** The entry row (the subject's newest), or null. */
        JsonNode entry(String subject) {
            return orNull(rows(subject).get("entry"));
        }

        /** How many outbox rows the subject has (its pending rows and at most one settled row). */
        long entryRows(String subject) {
            return rows(subject).get("entryRows").asLong();
        }

        /** The link row, or null. */
        JsonNode link(String subject) {
            return orNull(rows(subject).get("link"));
        }

        JsonNode health() {
            return orNull(rows("none").get("health"));
        }

        /** Waits for the mock to hold an account with this userName and returns it. */
        JsonNode awaitUser(String userName) {
            return until(WAIT, () -> mock.userByUserName(userName), u -> u != null);
        }

        /** Waits until the entry for the subject is done. */
        JsonNode awaitDone(String subject) {
            return until(WAIT, () -> entry(subject), e -> e != null && e.path("state").asText().equals("done"));
        }
    }

    protected Dest destination(String family, Map<String, List<String>> config) {
        return destination(family, config, Map.of());
    }

    /**
     * A destination of a fresh mock app; {@code mockConfig} is applied to the app before the destination is saved (and
     * resynced).
     */
    protected Dest destination(String family, Map<String, List<String>> config, Map<String, ?> mockConfig) {
        return destinationIn(realm, family, config, mockConfig);
    }

    protected Dest destinationIn(String realmName, String family, Map<String, List<String>> config,
            Map<String, ?> mockConfig) {
        String key = Keys.unique(family);
        if (!mockConfig.isEmpty()) {
            mockServer.admin(key).config(mockConfig);
        }
        Map<String, List<String>> c = new LinkedHashMap<>();
        c.put("baseUrl", List.of(scimBaseUrl(key)));
        c.put("token", List.of("t"));
        c.putAll(config);
        String id = admin.createDestination(realmName, family + "-dest", c).createdId();
        return new Dest(realmName, id, key);
    }

    protected Dest destinationForGroup(String family, String groupName, Map<String, List<String>> more) {
        Map<String, List<String>> c = new LinkedHashMap<>(more);
        c.put("scopeGroups", List.of(groupName));
        return destination(family, c);
    }

    /** The base URL a destination of app {@code key} uses (as the server reaches the mock). */
    protected String scimBaseUrl(String key) {
        return mockServer.scimBaseUrl(key);
    }

    /** {@code /realms/<realm>/scim-provisioning-test/<path>}. */
    protected String testResource(String path) {
        return "/realms/" + realm + "/" + TEST_RESOURCE + "/" + path;
    }

    /** {@code /admin/realms/<realm>/scim-provisioning/<path>}. */
    protected String provisioning(String path) {
        return "/admin/realms/" + realm + "/scim-provisioning/" + path;
    }

    protected static Map<String, List<String>> cfg(String... kv) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], List.of(kv[i + 1]));
        }
        return m;
    }

    protected static JsonNode orNull(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n;
    }

    /** A field that is absent or null. */
    protected static boolean absent(JsonNode n, String field) {
        return n == null || orNull(n.get(field)) == null;
    }

    protected static List<JsonNode> list(JsonNode n) {
        List<JsonNode> out = new ArrayList<>();
        if (n != null) {
            n.forEach(out::add);
        }
        return out;
    }

    protected static List<String> texts(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null) {
            n.forEach(x -> out.add(x.asText()));
        }
        return out;
    }

    protected static boolean method(JsonNode op, String method) {
        return op.path("method").asText().equals(method);
    }

    protected String user(String username) {
        return admin.createUser(realm, username, username + "@example.com", cap(username), "Tester");
    }

    protected static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- waiting

    /** Polls until {@code done} holds for the probe's value and returns it; probe exceptions count as "not yet". */
    protected static <T> T until(Duration timeout, Callable<T> probe, Predicate<T> done) {
        return await().atMost(timeout)
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .until(probe, x -> done.test(x));
    }

    protected static void until(Duration timeout, Callable<Boolean> condition) {
        await().atMost(timeout).pollInterval(Duration.ofMillis(200)).ignoreExceptions().until(condition);
    }

    /** Lets time pass, to show that something does <i>not</i> happen. */
    protected static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
