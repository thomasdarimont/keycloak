package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup half of the extension's {@code RestartStabilityTest}: the drainer starts from {@code PostMigrationEvent},
 * after Liquibase has created the tables, so no drainer tick ever runs against a missing table. This class has its own
 * server config, so the server starts for it (on the default {@code dev-mem} database, on a fresh database); the check
 * covers every server start of this JVM, including the very first one on a fresh database, except those of SSF-enabled
 * servers (whose own drainer has an upstream startup race, see {@link SsfCoexistenceTest}). A restart against a
 * database that already holds rows is not covered.
 */
@KeycloakIntegrationTest(config = DrainerStartupTest.FreshStartConfig.class)
public class DrainerStartupTest extends AbstractScimProvisioningTest {

    private static final Pattern MISSING_TABLE = Pattern.compile(
            "does not exist|doesn't exist|not found|no such table|invalid object name|undefined table",
            Pattern.CASE_INSENSITIVE);

    /** The default server, under a config class of its own so that it is started for this test. */
    public static class FreshStartConfig extends ScimProvisioningServerConfig {
    }

    private static boolean ours(String line) {
        String l = line.toUpperCase(Locale.ROOT);
        return l.contains("SCIM_PROV_") || l.contains("OUTBOX_ENTRY");
    }

    @Test
    public void theDrainerStartsAfterTheSchemaExistsAndDeliversRightAway() {
        List<String> startup = ServerLog.sinceClassStart();
        assertThat("the server started for this class", startup, hasItem(containsString(" started in ")));
        assertThat(startup, hasItem(containsString("scim-provisioning: enqueue mode transactional, drain every 500 ms")));

        // the tables exist and the entities are mapped
        JsonNode s = admin.anonymous("GET", testResource("schema"), null).ok().body();
        assertTrue(s.get("ScimProvisioningLink").isNumber());
        assertTrue(s.get("ScimProvisioningHealth").isNumber());
        assertEquals(1, s.get("changeSet").asLong());

        // the drainer runs: a change made right after startup is delivered
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String u = user("early");
        d.awaitUser("early");
        d.awaitDone("user:" + u);

        // and no tick ever ran against a missing table
        List<String> missing = ServerLog.excluding(SsfCoexistenceTest.SsfConfig.class).stream()
                .filter(l -> MISSING_TABLE.matcher(l).find())
                .filter(DrainerStartupTest::ours)
                .toList();
        assertThat(missing, empty());
    }
}
