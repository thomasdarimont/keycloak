package org.keycloak.tests.scim.provisioning;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * The invariant of the extension's {@code RestartStabilityTest} on one server (the framework cannot restart the server
 * in the middle of a test): once deliveries have settled, resyncs send no write. The users have multi-valued attributes
 * stored in an arbitrary order, so the delivered state (and its hash) must not depend on storage or load order. A true
 * restart is not covered here.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class ResyncIdempotenceTest extends AbstractScimProvisioningTest {

    private int backlog() {
        return admin.get(provisioning("status")).ok().body().get(0).get("backlog").asInt();
    }

    private void resyncAndSettle(Dest d, String probe) {
        String before = d.entry(probe).get("id").asText();
        admin.post(provisioning("resync?destination=" + d.id), null).ok();
        until(WAIT, () -> !d.entry(probe).get("id").asText().equals(before)); // the resync ran
        until(Duration.ofMinutes(2), () -> backlog() == 0);
        d.awaitDone(probe);
    }

    @Test
    public void resyncsAfterSettledDeliveriesSendNothing() {
        admin.allowUnmanagedAttributes(realm);
        Dest d = destination("atlassian", Map.of(
                "scopeEveryone", List.of("true"),
                "mapping", List.of(
                        "userName=username",
                        "externalId=id",
                        "name.givenName=firstName",
                        "emails[type eq \"work\"].value=email",
                        "urn:scim:schemas:extension:enterprise:1.0.department=attr:department")));
        String first = null;
        for (int i = 0; i < 30; i++) {
            String id = user("stable" + i);
            if (first == null) {
                first = id;
            }
            // several values in storage order: the smallest is sent, whatever order they come back in
            admin.updateUser(realm, id, Map.of("attributes", Map.of("department", List.of("z" + i, "a" + i, "m" + i))));
        }
        until(WAIT, () -> d.mock.users().size() == 30);
        until(WAIT, () -> backlog() == 0);
        int writes = d.mock.writes().size();

        String probe = "user:" + first;
        resyncAndSettle(d, probe);
        resyncAndSettle(d, probe);
        assertThat("no write after two resyncs", d.mock.writes(), hasSize(writes));
    }
}
