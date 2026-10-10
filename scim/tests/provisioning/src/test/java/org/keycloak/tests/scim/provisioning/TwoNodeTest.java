package org.keycloak.tests.scim.provisioning;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two clustered Keycloak nodes (the framework's {@code cluster} server: Docker containers on one database) with the app
 * slowed down so that a full batch would take longer than the drainer's lock TTL. Runs are time-boxed inside the TTL,
 * so each user is created exactly once. The nodes reach the mock on the host through
 * {@code host.testcontainers.internal}.
 *
 * <p>Runs only with {@code -Dkc.test.server=cluster -Dkc.test.database=postgres}.
 */
@EnabledIfSystemProperty(named = "kc.test.server", matches = "cluster")
@KeycloakIntegrationTest(config = TwoNodeTest.ClusterConfig.class)
public class TwoNodeTest extends AbstractScimProvisioningTest {

    static {
        // before the nodes start: they reach the test JVM's HTTP server (the mock) on this port
        Testcontainers.exposeHostPorts(8500);
    }

    public static class ClusterConfig extends ScimProvisioningServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            super.configure(config);
            provisioning(config, "lock-ttl-seconds", "6");
            provisioning(config, "run-time-box-millis", "2500");
            config.log().categoryLevel("org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner", "DEBUG");
            return config;
        }
    }

    @Override
    protected String scimBaseUrl(String key) {
        return mockServer.scimBaseUrl(key).replace("127.0.0.1", "host.testcontainers.internal");
    }

    @Test
    public void eachUserIsCreatedExactlyOnceAcrossTwoNodes() {
        // 40 users × (find + create) × 250 ms ≈ 20 s for one batch: three lock TTLs without the time box
        Dest d = destination("atlassian", Map.of("scopeEveryone", List.of("true")));
        d.mock.config(Map.of("latencyMs", 250));
        for (int i = 0; i < 40; i++) {
            user("node" + i);
        }

        until(Duration.ofMinutes(3), () -> d.mock.users().size() >= 40);
        // let both nodes keep running a while longer: a duplicate would show up now
        pause(8000);
        Map<String, Integer> posts = new HashMap<>();
        for (JsonNode o : d.mock.ops(o -> method(o, "POST"))) {
            posts.merge(o.path("userName").asText(), 1, Integer::sum);
            assertEquals(201, o.path("status").asInt(), "no conflict: nobody created twice");
        }
        assertThat(posts, aMapWithSize(40));
        assertThat(posts.values(), everyItem(is(1)));
        assertThat(d.mock.users(), hasSize(40));
        // the two nodes formed one cluster, so the drainer's lock is shared, and both competed for it
        List<String> log = ServerLog.sinceClassStart();
        assertThat(log, hasItem(matchesPattern(".*ISPN00009[34].*\\(2\\).*")));
        assertThat(log, hasItem(containsString(
                "Skipped execution of task outbox-drainer:scim-provisioning as other cluster node is executing it")));
    }
}
