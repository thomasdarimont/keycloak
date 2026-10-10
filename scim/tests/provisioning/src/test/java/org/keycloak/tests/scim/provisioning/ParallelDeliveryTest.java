package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

/**
 * A tick delivers to different destinations in parallel ({@code delivery-concurrency}): a slow app does not hold back
 * the changes for another app claimed in the same tick.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class ParallelDeliveryTest extends AbstractScimProvisioningTest {

    private static final int USERS = 6;

    @Test
    public void aSlowAppDoesNotHoldBackAnother() {
        String g = admin.createGroup(realm, "Engineering");
        Dest slow = destinationForGroup("atlassian", "Engineering", Map.of());
        Dest fast = destinationForGroup("atlassian", "Engineering", Map.of());
        slow.mock.config(Map.of("latencyMs", 1000));
        try {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < USERS; i++) {
                ids.add(user("par" + i));
            }
            for (String id : ids) {
                admin.joinGroup(realm, id, g);
            }

            // each user costs the slow app a lookup and a create, a second each
            until(WAIT, () -> fast.mock.users().size() == USERS);
            assertThat("the fast app was not served after the slow one", slow.mock.users().size(), lessThan(USERS / 2));

            until(WAIT, () -> slow.mock.users().size() == USERS);
        } finally {
            slow.mock.heal();
        }
    }
}
