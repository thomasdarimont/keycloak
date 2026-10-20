package org.keycloak.scim.provisioning.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.keycloak.scim.provisioning.hash.CanonicalJson;
import org.keycloak.scim.provisioning.hash.Hashing;
import org.keycloak.scim.resource.Scim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserResourceBuilderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The person of RFC 7643 §8.2 ("Babs Jensen"), as Keycloak would hold her. */
    private static final TestSubject BABS =
            TestSubject.of("701984", "bjensen@example.com", "bjensen@example.com", "Barbara", "Jensen");

    @Test
    public void defaultMappingProducesTheRfc7643CoreUserShape() throws Exception {
        ObjectNode user = UserResourceBuilder.build(BABS, Mapping.defaults(), true);

        // The RFC 7643 §8.2 attributes that the default mapping carries, with the values the §8.2
        // example uses for them; displayName is derived ("First Last").
        JsonNode expected = JSON.readTree(
                "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                        + "\"externalId\":\"701984\","
                        + "\"userName\":\"bjensen@example.com\","
                        + "\"name\":{\"familyName\":\"Jensen\",\"givenName\":\"Barbara\"},"
                        + "\"displayName\":\"Barbara Jensen\","
                        + "\"emails\":[{\"value\":\"bjensen@example.com\",\"type\":\"work\",\"primary\":true}],"
                        + "\"active\":true}");
        assertEquals(CanonicalJson.toString(expected), CanonicalJson.toString(user));
    }

    @Test
    public void nullFieldsAreOmitted() {
        TestSubject noNames = TestSubject.of("1", "u1", null, null, "  ");
        ObjectNode user = UserResourceBuilder.build(noNames, Mapping.defaults(), true);
        assertFalse(user.has("name"));
        assertFalse(user.has("emails"));
        assertEquals("u1", user.get("displayName").asText());
    }

    @Test
    public void activeIsAlwaysPresentEvenWhenUnmapped() {
        Mapping m = Mapping.parse(List.of("userName=username"));
        ObjectNode user = UserResourceBuilder.build(BABS.disabled(), m, false);
        assertTrue(user.get("active").isBoolean());
        assertFalse(user.get("active").booleanValue());
    }

    @Test
    public void firstEntryOfAFilteredArrayIsPrimary() {
        Mapping m = Mapping.parse(List.of(
                "userName=username",
                "emails[type eq \"work\"].value=email",
                "emails[type eq \"other\"].value=attr:altMail",
                "emails[type eq \"work\"].display=fullName"));
        ObjectNode user = UserResourceBuilder.build(BABS.withAttributes(Map.of("altMail", List.of("b@home"))), m, true);
        JsonNode emails = user.get("emails");
        assertEquals(2, emails.size());
        // rules run in code-point order: "other" < "work", so "other" is created first
        assertEquals("other", emails.get(0).get("type").asText());
        assertTrue(emails.get(0).get("primary").booleanValue());
        assertFalse(emails.get(1).has("primary"));
        assertEquals("Barbara Jensen", emails.get(1).get("display").asText());
        assertEquals("bjensen@example.com", emails.get(1).get("value").asText());
    }

    @Test
    public void schemasListCoreThenExtensionsInRuleOrder() {
        Mapping m = Mapping.parse(List.of(
                "userName=username",
                "urn:scim:schemas:extension:enterprise:1.0.department=attr:department",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber=attr:emp",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.value=attr:mgr"));
        ObjectNode user = UserResourceBuilder.build(
                BABS.withAttributes(Map.of(
                        "department", List.of("Tour Operations"),
                        "emp", List.of("701984"),
                        "mgr", List.of("26118915-6090-4610-87e4-49d8ca9f808d"))),
                m,
                true);
        assertEquals("[\"" + Scim.USER_CORE_SCHEMA + "\",\"" + Scim.ENTERPRISE_USER_SCHEMA
                + "\",\"urn:scim:schemas:extension:enterprise:1.0\"]", user.get("schemas").toString());
        // RFC 7643 §3.3 / §4.3: extension attributes live under a top-level key named by the URN
        assertEquals("701984", user.get(Scim.ENTERPRISE_USER_SCHEMA).get("employeeNumber").asText());
        assertEquals("26118915-6090-4610-87e4-49d8ca9f808d",
                user.get(Scim.ENTERPRISE_USER_SCHEMA).get("manager").get("value").asText());
        assertEquals("Tour Operations",
                user.get("urn:scim:schemas:extension:enterprise:1.0").get("department").asText());
    }

    @Test
    public void extensionWithoutValueIsNotListed() {
        Mapping m = Mapping.parse(List.of(
                "userName=username",
                "urn:scim:schemas:extension:enterprise:1.0.department=attr:department"));
        ObjectNode user = UserResourceBuilder.build(BABS, m, true);
        assertEquals(1, user.get("schemas").size());
        assertFalse(user.has("urn:scim:schemas:extension:enterprise:1.0"));
    }

    @Test
    public void outputIsIdenticalForShuffledRuleOrder() {
        List<String> lines = new ArrayList<>(List.of(
                "userName=email",
                "externalId=id",
                "displayName=displayName",
                "name.givenName=firstName",
                "name.familyName=lastName",
                "name.formatted=fullName",
                "emails[type eq \"work\"].value=email",
                "title=const:Tour Guide",
                "urn:scim:schemas:extension:enterprise:1.0.department=attr:department"));
        TestSubject s = BABS.withAttributes(Map.of("department", List.of("Tour")));
        String reference = CanonicalJson.toString(UserResourceBuilder.build(s, Mapping.parse(lines), true));
        String referenceText = UserResourceBuilder.build(s, Mapping.parse(lines), true).toString();
        Random r = new Random(42);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(lines, r);
            ObjectNode u = UserResourceBuilder.build(s, Mapping.parse(lines), true);
            assertEquals(reference, CanonicalJson.toString(u));
            // not only the canonical form: rules are sorted, so even the tree is built the same way
            assertEquals(referenceText, u.toString());
        }
    }

    @Test
    public void booleansAreBooleans() {
        Mapping m = Mapping.parse(List.of("userName=username", "urn:example:ext:1.0.isActive=enabled", "title=const:true"));
        ObjectNode user = UserResourceBuilder.build(BABS, m, true);
        assertTrue(user.get("active").isBoolean());
        assertTrue(user.get("urn:example:ext:1.0").get("isActive").isBoolean());
        assertTrue(user.get("title").isTextual());
    }

    @Test
    public void multiValuedUserAttributeSendsTheSmallestValue() {
        // Storage order of attribute values is not stable; the smallest value is.
        Mapping m = Mapping.parse(List.of("userName=username", "title=attr:department"));
        String a = Hashing.hash(UserResourceBuilder.build(
                BABS.withAttributes(Map.of("department", List.of("Sales", "Engineering", "Ops"))), m, true));
        String b = Hashing.hash(UserResourceBuilder.build(
                BABS.withAttributes(Map.of("department", List.of("Ops", "Sales", "Engineering"))), m, true));
        assertEquals(a, b);
        assertEquals("Engineering", UserResourceBuilder.build(
                BABS.withAttributes(Map.of("department", List.of("Sales", "Engineering"))), m, true)
                .get("title").asText());
    }

    @Test
    public void fullAndDisplayNameRules() {
        assertEquals("Ann", new FieldRef.FullName().resolve(TestSubject.of("1", "u", null, " Ann ", null)));
        assertNull(new FieldRef.FullName().resolve(TestSubject.of("1", "u", null, null, null)));
        assertEquals("u", new FieldRef.DisplayName().resolve(TestSubject.of("1", "u", null, null, null)));
        assertEquals("A B", new FieldRef.DisplayName().resolve(TestSubject.of("1", "u", null, "A", "B")));
    }

    // ---- apply: a destination's overrides on top of an already rendered user ----

    private static ObjectNode rendered() {
        return UserResourceBuilder.build(BABS, Mapping.defaults(), true);
    }

    @Test
    public void applyWithoutOverridesChangesNothing() {
        ObjectNode root = rendered();
        ObjectNode before = root.deepCopy();
        UserResourceBuilder.apply(root, BABS, Mapping.NONE);
        UserResourceBuilder.apply(root, BABS, Mapping.overrides(List.of()));
        assertEquals(before, root);
    }

    @Test
    public void applyReplacesAddressedValuesAndKeepsTheRest() {
        ObjectNode root = rendered();
        ObjectNode before = root.deepCopy();
        TestSubject s = BABS.withAttributes(Map.of("nick", List.of("Babs")));
        UserResourceBuilder.apply(root, s, Mapping.overrides(List.of(
                "displayName=attr:nick",
                "name.givenName=attr:nick",
                "title=const:Tour Guide")));

        assertEquals("Babs", root.get("displayName").asText());
        assertEquals("Babs", root.get("name").get("givenName").asText());
        // a sibling of an overridden sub-attribute stays
        assertEquals("Jensen", root.get("name").get("familyName").asText());
        assertEquals("Tour Guide", root.get("title").asText());
        // everything not addressed is as rendered
        for (String field : List.of("userName", "externalId", "emails", "active", "schemas")) {
            assertEquals(before.get(field), root.get(field), field);
        }
    }

    @Test
    public void applyAddsAnExtensionUrnToSchemasOnce() {
        Mapping overrides = Mapping.overrides(List.of(
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department=attr:department",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber=attr:emp"));
        TestSubject s = BABS.withAttributes(Map.of("department", List.of("Tour"), "emp", List.of("42")));
        ObjectNode root = rendered();
        UserResourceBuilder.apply(root, s, overrides);
        UserResourceBuilder.apply(root, s, overrides); // again: still listed once
        assertEquals("[\"" + Scim.USER_CORE_SCHEMA + "\",\"" + Scim.ENTERPRISE_USER_SCHEMA + "\"]",
                root.get("schemas").toString());
        assertEquals("Tour", root.get(Scim.ENTERPRISE_USER_SCHEMA).get("department").asText());
        assertEquals("42", root.get(Scim.ENTERPRISE_USER_SCHEMA).get("employeeNumber").asText());

        // a rendering that already lists the URN (and holds other extension values) keeps one entry and its values
        ObjectNode withExtension = rendered();
        withExtension.withArray("schemas").add(Scim.ENTERPRISE_USER_SCHEMA);
        withExtension.putObject(Scim.ENTERPRISE_USER_SCHEMA).put("costCenter", "4130");
        UserResourceBuilder.apply(withExtension, s, overrides);
        assertEquals(2, withExtension.get("schemas").size());
        assertEquals("4130", withExtension.get(Scim.ENTERPRISE_USER_SCHEMA).get("costCenter").asText());
        assertEquals("Tour", withExtension.get(Scim.ENTERPRISE_USER_SCHEMA).get("department").asText());
    }

    @Test
    public void applyOfANullValuedRuleChangesNothing() {
        ObjectNode root = rendered();
        ObjectNode before = root.deepCopy();
        UserResourceBuilder.apply(root, BABS, Mapping.overrides(List.of(
                "displayName=attr:missing",
                "title=attr:missing",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department=attr:missing")));
        assertEquals(before, root);
    }

    @Test
    public void applyRewritesTheExistingWorkEmail() {
        ObjectNode root = rendered();
        TestSubject s = BABS.withAttributes(Map.of("corpMail", List.of("babs@corp.example.com")));
        UserResourceBuilder.apply(root, s, Mapping.overrides(List.of("emails[type eq \"work\"].value=attr:corpMail")));
        JsonNode emails = root.get("emails");
        assertEquals(1, emails.size());
        assertEquals("babs@corp.example.com", emails.get(0).get("value").asText());
        assertEquals("work", emails.get(0).get("type").asText());
        assertTrue(emails.get(0).get("primary").booleanValue());
    }
}
