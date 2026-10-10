package org.keycloak.tests.scim.provisioning.delivery;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.delivery.DeliveryResult;
import org.keycloak.scim.provisioning.delivery.DeprovisionMode;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.delivery.UserDelivery;
import org.keycloak.scim.provisioning.hash.Hashing;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.mapping.UserResourceBuilder;
import org.keycloak.scim.resource.user.User;
import org.keycloak.tests.scim.provisioning.mock.Keys;
import org.keycloak.tests.scim.provisioning.mock.MockScimAdmin;
import org.keycloak.tests.scim.provisioning.mock.MockScimServer;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User upsert and deprovision against the mock, with links in memory: adoption, crash safety, deactivate/delete, the
 * PUT fallback and tombstones.
 */
public class UserDeliveryTest {

    private static MockScimServer mock;

    private final InMemoryLinkStore store = new InMemoryLinkStore();
    private MockScimAdmin admin;
    private RecordingHttpClient http;
    private UserDelivery delivery;

    @BeforeAll
    public static void start() {
        mock = MockScimServer.start();
    }

    @AfterAll
    public static void stop() {
        mock.close();
    }

    @AfterEach
    public void closeClient() throws IOException {
        if (http != null) {
            http.close();
        }
    }

    private void app(String family) {
        String key = Keys.unique(family);
        admin = mock.admin(key);
        http = new RecordingHttpClient(HttpClientBuilder.create().build());
        delivery = new UserDelivery(
                ScimEndpoint.create(http, mock.baseUrl(key), () -> "t", Duration.ofSeconds(5)), Clock.systemUTC());
    }

    /** What the drainer does: hash the JSON tree, send it as a {@link User}. */
    private static User toUser(ObjectNode resource) {
        try {
            return JsonSerialization.mapper.treeToValue(resource, User.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectNode resource(TestSubject s) {
        return UserResourceBuilder.build(s, Mapping.defaults(), s.enabled());
    }

    private DeliveryResult upsert(TestSubject s) throws ScimException {
        ObjectNode r = resource(s);
        String subject = "user:" + s.id();
        return UserDelivery.apply(store, delivery.upsert(subject, toUser(r), Hashing.hash(r), store.find(subject)));
    }

    private DeliveryResult deprovision(TestSubject s, DeprovisionMode mode, boolean forced) throws ScimException {
        String subject = "user:" + s.id();
        return UserDelivery.apply(store, delivery.deprovision(subject, s.username(), store.find(subject), mode, forced));
    }

    private long count(String method) {
        return admin.ops(o -> o.path("method").asText().equals(method)).size();
    }

    private LinkRecord link(String subject) {
        return store.find(subject).orElseThrow();
    }

    private static boolean created(DeliveryResult r) {
        return assertInstanceOf(DeliveryResult.Delivered.class, r).created();
    }

    private static final TestSubject ANN = TestSubject.of("u-ann", "ann", "ann@example.com", "Ann", "Lee");

    @Test
    public void createsAndLinks() throws Exception {
        app("atlassian");
        assertTrue(created(upsert(ANN)));
        JsonNode remote = admin.userByUserName("ann");
        assertEquals("u-ann", remote.get("externalId").asText());
        assertEquals("Ann", remote.get("name").get("givenName").asText());
        LinkRecord link = link("user:u-ann");
        assertEquals(remote.get("id").asText(), link.remoteId());
        assertTrue(link.isLive());
        // one lookup, one create
        assertEquals(1, count("GET"));
        assertEquals(1, count("POST"));
    }

    @Test
    public void unchangedStateSendsNothing() throws Exception {
        app("atlassian");
        upsert(ANN);
        int before = admin.ops().size();
        assertInstanceOf(DeliveryResult.Skipped.class, upsert(ANN));
        assertThat(admin.ops(), hasSize(before));
    }

    @Test
    public void changedStateIsAPut() throws Exception {
        app("atlassian");
        upsert(ANN);
        upsert(TestSubject.of("u-ann", "ann", "ann@example.com", "Annie", "Lee"));
        assertEquals(1, count("PUT"));
        assertEquals("Annie", admin.userByUserName("ann").get("name").get("givenName").asText());
    }

    /** An account that already exists is adopted, never duplicated. */
    @Test
    public void adoptsAnExistingAccountByUserName() throws Exception {
        app("atlassian");
        String seeded = admin.seedUser(Map.of("userName", "ann", "active", true)).get("id").asText();
        upsert(ANN);
        assertEquals(seeded, link("user:u-ann").remoteId());
        assertEquals(0, count("POST"));
        assertThat(admin.users(), hasSize(1));
    }

    @Test
    public void adoptionTriesLowerCaseUserNameSecond() throws Exception {
        app("atlassian");
        String seeded = admin.seedUser(Map.of("userName", "mixed@example.com")).get("id").asText();
        // the destination filters case-sensitively here: only an exact match is returned
        http.interceptor = req -> {
            String q = req.uri().getRawQuery();
            if (req.method().equals("GET") && q != null && q.contains("Mixed")) {
                return emptyList();
            }
            return null;
        };
        upsert(TestSubject.of("u-m", "Mixed@example.com", null, null, null));
        assertEquals(seeded, link("user:u-m").remoteId());
        assertEquals(0, count("POST"));
    }

    @Test
    public void conflictOnCreateIsResolvedByFindAndAdopt() throws Exception {
        app("atlassian");
        String seeded = admin.seedUser(Map.of("userName", "ann")).get("id").asText();
        // the first lookup misses (e.g. an eventually consistent search), the create then conflicts
        boolean[] first = {true};
        http.interceptor = req -> {
            if (req.method().equals("GET") && first[0]) {
                first[0] = false;
                return emptyList();
            }
            return null;
        };
        assertFalse(created(upsert(ANN)));
        assertEquals(seeded, link("user:u-ann").remoteId());
        assertEquals(409, admin.ops(o -> o.path("method").asText().equals("POST")).get(0).path("status").asInt());
        assertThat(admin.users(), hasSize(1));
    }

    @Test
    public void notFoundOnPutRecreates() throws Exception {
        app("atlassian");
        store.save(LinkRecord.live("user:u-ann", "deleted-in-the-app", "old-hash", true, "ann", 0));
        assertTrue(created(upsert(ANN)));
        String newId = admin.userByUserName("ann").get("id").asText();
        assertEquals(newId, link("user:u-ann").remoteId());
    }

    /** A crash after the destination's 201, before the link is stored. */
    @Test
    public void crashAfterCreateThenRerunCreatesExactlyOneAccount() throws Exception {
        app("atlassian");
        store.failSaves = true;
        assertThrows(IllegalStateException.class, () -> upsert(ANN));
        assertTrue(store.find("user:u-ann").isEmpty());
        store.failSaves = false;
        upsert(ANN); // the restart: no link, so find-before-create adopts the account just created
        assertEquals(1, count("POST"));
        assertThat(admin.users(), hasSize(1));
        assertEquals(admin.users().get(0).get("id").asText(), link("user:u-ann").remoteId());
    }

    @Test
    public void deactivate() throws Exception {
        app("atlassian");
        upsert(ANN);
        assertInstanceOf(DeliveryResult.Delivered.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, false));
        assertFalse(admin.userByUserName("ann").get("active").asBoolean());
        assertEquals(1, count("PATCH"));
        LinkRecord tomb = link("user:u-ann");
        assertTrue(tomb.deprovisioned());
        assertNull(tomb.payloadHash());
    }

    @Test
    public void delete() throws Exception {
        app("atlassian");
        upsert(ANN);
        deprovision(ANN, DeprovisionMode.DELETE, false);
        assertThat(admin.users(), empty());
        assertEquals(1, count("DELETE"));
        assertTrue(link("user:u-ann").deprovisioned());
    }

    @Test
    public void patchNotImplementedFallsBackToPutWithReadOnlyAttributesStripped() throws Exception {
        app("atlassian");
        upsert(ANN);
        admin.config(Map.of("failNext", 1, "failStatus", 501));
        http.requests.clear();
        deprovision(ANN, DeprovisionMode.DEACTIVATE, false);
        assertEquals(List.of("PATCH", "GET", "PUT"), http.requests.stream().map(RecordingHttpClient.Request::method).toList());
        JsonNode put = JsonSerialization.mapper.readTree(http.requests.get(2).body());
        assertFalse(put.has("meta"));
        assertFalse(put.has("id"));
        assertFalse(put.has("groups"));
        assertFalse(put.get("active").asBoolean());
        assertEquals("ann", put.get("userName").asText());
        assertFalse(admin.userByUserName("ann").get("active").asBoolean());
    }

    @Test
    public void otherPatchFailuresAreNotSwallowed() throws Exception {
        app("atlassian");
        upsert(ANN);
        admin.config(Map.of("failNext", 1, "failStatus", 503));
        ScimException e = assertThrows(ScimException.class, () -> deprovision(ANN, DeprovisionMode.DEACTIVATE, false));
        assertEquals(ErrorClass.SERVER_ERROR, e.errorClass());
        assertTrue(link("user:u-ann").isLive());
    }

    @Test
    public void repeatDeprovisionIsANoOp() throws Exception {
        app("atlassian");
        upsert(ANN);
        deprovision(ANN, DeprovisionMode.DEACTIVATE, false);
        int before = admin.ops().size();
        assertInstanceOf(DeliveryResult.Skipped.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, false));
        assertInstanceOf(DeliveryResult.Skipped.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, true));
        assertThat(admin.ops(), hasSize(before));
    }

    @Test
    public void deprovisionWithoutALinkSendsNothingUnlessForced() throws Exception {
        app("atlassian");
        admin.seedUser(Map.of("userName", "ann", "active", true));
        assertInstanceOf(DeliveryResult.Skipped.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, false));
        assertThat(admin.ops(), empty());
        // forced: the account may predate us — look it up and take it away
        assertInstanceOf(DeliveryResult.Delivered.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, true));
        assertFalse(admin.userByUserName("ann").get("active").asBoolean());
    }

    @Test
    public void returningUserIsRecreatedAfterDelete() throws Exception {
        app("atlassian");
        upsert(ANN);
        String first = link("user:u-ann").remoteId();
        deprovision(ANN, DeprovisionMode.DELETE, false);
        assertTrue(created(upsert(ANN)));
        LinkRecord link = link("user:u-ann");
        assertTrue(link.isLive());
        assertNotEquals(first, link.remoteId());
        assertEquals(0, count("PUT")); // never written to the stale id
    }

    @Test
    public void returningUserIsReactivatedAfterDeactivate() throws Exception {
        app("atlassian");
        upsert(ANN);
        deprovision(ANN, DeprovisionMode.DEACTIVATE, false);
        upsert(ANN);
        assertTrue(admin.userByUserName("ann").get("active").asBoolean());
        assertThat(admin.users(), hasSize(1));
        assertTrue(link("user:u-ann").isLive());
    }

    @Test
    public void aForcedDeleteAfterADeactivationDeletesTheAccount() throws Exception {
        app("atlassian");
        upsert(ANN);
        deprovision(ANN, DeprovisionMode.DEACTIVATE, false);
        assertThat(admin.users(), hasSize(1));
        // an unforced or deactivating repeat is still a no-op …
        assertInstanceOf(DeliveryResult.Skipped.class, deprovision(ANN, DeprovisionMode.DELETE, false));
        assertInstanceOf(DeliveryResult.Skipped.class, deprovision(ANN, DeprovisionMode.DEACTIVATE, true));
        // … but a forced delete goes through, and repeating it is harmless (404 counts as done)
        assertInstanceOf(DeliveryResult.Delivered.class, deprovision(ANN, DeprovisionMode.DELETE, true));
        assertThat(admin.users(), empty());
        assertInstanceOf(DeliveryResult.Delivered.class, deprovision(ANN, DeprovisionMode.DELETE, true));
        assertTrue(link("user:u-ann").deprovisioned());
    }

    @Test
    public void slackFamilyDeleteDeactivates() throws Exception {
        app("slack");
        upsert(ANN);
        deprovision(ANN, DeprovisionMode.DELETE, false);
        assertFalse(admin.userByUserName("ann").get("active").asBoolean());
    }

    @Test
    public void disabledUserIsDeliveredInactive() throws Exception {
        app("atlassian");
        upsert(ANN.disabled());
        assertFalse(admin.userByUserName("ann").get("active").asBoolean());
        assertFalse(link("user:u-ann").active());
    }

    @Test
    public void mappingFromConfigIsSentAsBuilt() throws Exception {
        app("atlassian");
        Mapping m = Mapping.parse(List.of(
                "userName=email",
                "externalId=id",
                "urn:scim:schemas:extension:enterprise:1.0.department=attr:department"));
        TestSubject s = ANN.withAttributes(Map.of("department", List.of("Eng")));
        ObjectNode r = UserResourceBuilder.build(s, m, true);
        delivery.upsert("user:u-ann", toUser(r), Hashing.hash(r), Optional.empty());
        JsonNode remote = admin.userByUserName("ann@example.com");
        assertEquals("Eng", remote.get("urn:scim:schemas:extension:enterprise:1.0").get("department").asText());
    }

    @Test
    public void overridesOnTheRenderedUserAreSent() throws Exception {
        app("atlassian");
        TestSubject s = ANN.withAttributes(Map.of("dept", List.of("Eng"), "corpMail", List.of("ann@corp.example.com")));
        ObjectNode r = resource(s);
        UserResourceBuilder.apply(r, s, Mapping.overrides(List.of(
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department=attr:dept",
                "emails[type eq \"work\"].value=attr:corpMail")));
        assertInstanceOf(DeliveryResult.Delivered.class,
                delivery.upsert("user:u-ann", toUser(r), Hashing.hash(r), Optional.empty()));
        JsonNode remote = admin.userByUserName("ann");
        assertEquals("Eng",
                remote.get("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User").get("department").asText());
        assertEquals(1, remote.get("emails").size());
        assertEquals("ann@corp.example.com", remote.get("emails").get(0).get("value").asText());
        assertEquals("u-ann", remote.get("externalId").asText());
    }

    private static HttpResponse emptyList() {
        return RecordingHttpClient.json(200,
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:ListResponse\"],\"totalResults\":0,\"Resources\":[]}");
    }
}
