package org.keycloak.tests.scim.provisioning.delivery;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.delivery.DeliveryResult;
import org.keycloak.scim.provisioning.delivery.GroupDelivery;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.delivery.UserDelivery;
import org.keycloak.scim.provisioning.mapping.GroupResourceBuilder;
import org.keycloak.tests.scim.provisioning.mock.Keys;
import org.keycloak.tests.scim.provisioning.mock.MockScimAdmin;
import org.keycloak.tests.scim.provisioning.mock.MockScimServer;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group delivery against the mock: member id translation, deferral, adoption by displayName, PATCH and re-create. */
public class GroupDeliveryTest {

    private static MockScimServer mock;
    private static CloseableHttpClient http;

    private final InMemoryLinkStore store = new InMemoryLinkStore();
    private MockScimAdmin admin;
    private ScimEndpoint client;
    private GroupDelivery delivery;

    @BeforeAll
    public static void start() {
        mock = MockScimServer.start();
        http = HttpClientBuilder.create().build();
    }

    @AfterAll
    public static void stop() throws IOException {
        http.close();
        mock.close();
    }

    @BeforeEach
    public void app() {
        String key = Keys.unique("atlassian");
        admin = mock.admin(key);
        client = ScimEndpoint.create(http, mock.baseUrl(key), () -> "t", Duration.ofSeconds(5));
        delivery = new GroupDelivery(client, Clock.systemUTC(), Duration.ofSeconds(5));
    }

    private String seedUser(String userName) {
        return admin.seedUser(Map.of("userName", userName)).get("id").asText();
    }

    private DeliveryResult deliver(List<GroupDelivery.Member> members, int deferrals) throws ScimException {
        return UserDelivery.apply(store,
                delivery.deliver("group:g1", "Engineering", "g1", members, store.find("group:g1"), deferrals, 3));
    }

    private static List<String> memberValues(JsonNode group) {
        List<String> out = new ArrayList<>();
        group.path("members").forEach(m -> out.add(m.get("value").asText()));
        return out;
    }

    @Test
    public void createsWithTranslatedMemberIds() throws Exception {
        String a = seedUser("a");
        String b = seedUser("b");
        DeliveryResult r = deliver(List.of(
                new GroupDelivery.Member("user:1", a, false),
                new GroupDelivery.Member("user:2", b, true)), 0);
        assertInstanceOf(DeliveryResult.Delivered.class, r);
        JsonNode g = admin.groupByDisplayName("Engineering");
        assertEquals("g1", g.get("externalId").asText());
        assertThat(memberValues(g), containsInAnyOrder(a, b));
    }

    @Test
    public void defersWhileAMemberHasNotLanded() throws Exception {
        String a = seedUser("a");
        List<GroupDelivery.Member> members = List.of(
                new GroupDelivery.Member("user:1", a, false),
                new GroupDelivery.Member("user:2", null, true));
        DeliveryResult.Deferred d = assertInstanceOf(DeliveryResult.Deferred.class, deliver(members, 0));
        assertEquals(Duration.ofSeconds(5), d.delay());
        assertThat(admin.ops(o -> o.path("type").asText().equals("Group")), empty());
        assertTrue(store.find("group:g1").isEmpty());
    }

    @Test
    public void deferralIsBounded() throws Exception {
        String a = seedUser("a");
        List<GroupDelivery.Member> members = List.of(
                new GroupDelivery.Member("user:1", a, false),
                new GroupDelivery.Member("user:2", null, true));
        assertInstanceOf(DeliveryResult.Deferred.class, deliver(members, 2));
        // after maxDeferrals the group goes out without the member that has not landed
        assertInstanceOf(DeliveryResult.Delivered.class, deliver(members, 3));
        assertThat(memberValues(admin.groupByDisplayName("Engineering")), contains(a));
    }

    @Test
    public void memberWithoutAccountAndNothingPendingIsLeftOutWithoutDeferral() throws Exception {
        String a = seedUser("a");
        DeliveryResult r = deliver(List.of(
                new GroupDelivery.Member("user:1", a, false),
                new GroupDelivery.Member("user:2", null, false)), 0);
        assertInstanceOf(DeliveryResult.Delivered.class, r);
        assertThat(memberValues(admin.groupByDisplayName("Engineering")), contains(a));
    }

    @Test
    public void findsAnExistingGroupByDisplayNameBeforeCreating() throws Exception {
        String existing = client.createGroup(GroupResourceBuilder.build("Engineering", null, List.of())).getId();
        String a = seedUser("a");
        deliver(List.of(new GroupDelivery.Member("user:1", a, false)), 0);
        assertThat(admin.groups(), hasSize(1));
        assertEquals(existing, store.find("group:g1").orElseThrow().remoteId());
        assertThat(memberValues(admin.groups().get(0)), contains(a));
    }

    @Test
    public void membershipChangeIsAPatchAndUnchangedIsSkipped() throws Exception {
        String a = seedUser("a");
        String b = seedUser("b");
        deliver(List.of(new GroupDelivery.Member("user:1", a, false)), 0);
        deliver(List.of(
                new GroupDelivery.Member("user:1", a, false),
                new GroupDelivery.Member("user:2", b, false)), 0);
        assertThat(admin.ops(o -> o.path("method").asText().equals("PATCH")), hasSize(1));
        assertThat(memberValues(admin.groupByDisplayName("Engineering")), containsInAnyOrder(a, b));
        int before = admin.ops().size();
        assertInstanceOf(DeliveryResult.Skipped.class, deliver(List.of(
                new GroupDelivery.Member("user:2", b, false),
                new GroupDelivery.Member("user:1", a, false)), 0));
        assertThat(admin.ops(), hasSize(before));
    }

    @Test
    public void notFoundRecreates() throws Exception {
        String a = seedUser("a");
        store.save(LinkRecord.live("group:g1", "gone", "stale", true, "Engineering", 0));
        DeliveryResult.Delivered d = assertInstanceOf(DeliveryResult.Delivered.class,
                deliver(List.of(new GroupDelivery.Member("user:1", a, false)), 0));
        assertTrue(d.created());
        assertEquals(admin.groupByDisplayName("Engineering").get("id").asText(),
                store.find("group:g1").orElseThrow().remoteId());
    }

    @Test
    public void hashIsStableForTheSameMembership() {
        assertEquals(
                JsonSerialization.mapper.valueToTree(GroupResourceBuilder.build("G", "x", List.of("a", "b"))),
                JsonSerialization.mapper.valueToTree(GroupResourceBuilder.build("G", "x", List.of("b", "a", "a"))));
    }
}
