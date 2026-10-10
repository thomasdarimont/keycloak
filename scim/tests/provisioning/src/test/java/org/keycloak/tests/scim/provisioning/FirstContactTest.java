package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Two requests that touch a subject with no outbox row yet, at the same time, both succeed. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class FirstContactTest extends AbstractScimProvisioningTest {

    @Test
    public void concurrentFirstContactDoesNotFailTheAdminsRequests() {
        String g1 = admin.createGroup(realm, "One");
        String g2 = admin.createGroup(realm, "Two");
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            users.add(user("fc" + i));
        }
        Dest d = destination("atlassian", Map.of("scopeGroups", List.of("One", "Two")));

        for (String u : users) {
            CompletableFuture<Integer> a = CompletableFuture.supplyAsync(
                    () -> admin.put("/admin/realms/" + realm + "/users/" + u + "/groups/" + g1, Map.of()).status());
            CompletableFuture<Integer> b = CompletableFuture.supplyAsync(
                    () -> admin.put("/admin/realms/" + realm + "/users/" + u + "/groups/" + g2, Map.of()).status());
            assertEquals(204, a.join());
            assertEquals(204, b.join());
        }
        for (String u : users) {
            d.awaitDone("user:" + u);
        }
        assertThat(d.mock.users(), hasSize(users.size()));
    }
}
