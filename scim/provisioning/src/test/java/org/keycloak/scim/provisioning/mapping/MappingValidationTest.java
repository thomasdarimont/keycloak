package org.keycloak.scim.provisioning.mapping;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MappingValidationTest {

    private static void singleProblem(List<String> problems, String containing) {
        assertThat(problems, hasSize(1));
        assertThat(problems.get(0), containsString(containing));
    }

    @Test
    public void emptyMappingIsTheDefault() {
        assertEquals(Mapping.defaults(), Mapping.parse(List.of()));
        assertEquals(Mapping.defaults(), Mapping.parse(null));
        assertEquals(Mapping.defaults(), Mapping.parse(List.of(" ", "")));
        assertThat(Mapping.defaults().rules(), hasSize(7));
    }

    @Test
    public void emptyOverridesAreNone() {
        assertSame(Mapping.NONE, Mapping.overrides(List.of()));
        assertSame(Mapping.NONE, Mapping.overrides(null));
        assertSame(Mapping.NONE, Mapping.overrides(List.of(" ", "")));
        assertTrue(Mapping.NONE.isEmpty());
        assertThat(Mapping.validate(List.of()), empty());
        assertThat(Mapping.validate(null), empty());
        assertThat(Mapping.validate(List.of(" ")), empty());
    }

    @Test
    public void duplicatePathIsAnError() {
        singleProblem(Mapping.validate(List.of("userName=username", "USERNAME=email")), "duplicate");
    }

    @Test
    public void duplicateAcrossUrnFormsIsAnError() {
        singleProblem(Mapping.validate(
                List.of("userName=username", "urn:ietf:params:scim:schemas:core:2.0:User:userName=email")),
                "duplicate");
    }

    @Test
    public void unknownFieldIsAnError() {
        singleProblem(Mapping.validate(List.of("userName=username", "title=jobTitle")),
                "unknown user field 'jobTitle'");
    }

    @Test
    public void everyProblemIsReported() {
        MappingException e = assertThrows(MappingException.class,
                () -> Mapping.parse(List.of("userName", "title=nope", "1x=email")));
        assertThat(e.problems(), hasSize(3));
        MappingException o = assertThrows(MappingException.class,
                () -> Mapping.overrides(List.of("userName", "title=nope", "1x=email")));
        assertThat(o.problems(), hasSize(3));
    }

    /** A full mapping needs a userName; overrides apply on top of a rendering that has one. */
    @Test
    public void userNameIsRequiredOnlyInAFullMapping() {
        MappingException e = assertThrows(MappingException.class, () -> Mapping.parse(List.of("externalId=id")));
        assertThat(e.problems(), hasSize(1));
        assertThat(e.problems().get(0), containsString("userName"));

        assertThat(Mapping.validate(List.of("externalId=id")), empty());
        assertThat(Mapping.overrides(List.of("externalId=id")).lines(), contains("externalId=id"));
    }

    @Test
    public void conflictingShapesAreAnError() {
        singleProblem(Mapping.validate(List.of("userName=username", "name=fullName", "name.givenName=firstName")),
                "both");
    }

    @Test
    public void multiValuedCoreAttributeNeedsAFilter() {
        singleProblem(Mapping.validate(List.of("userName=username", "emails.value=email")), "multi-valued");
    }

    @Test
    public void reservedAttributesCannotBeMapped() {
        assertThat(Mapping.validate(List.of("userName=username", "id=id")), hasSize(1));
        assertThat(Mapping.validate(List.of("userName=username", "groups[type eq \"x\"].value=id")), hasSize(1));
        assertThat(Mapping.validate(List.of("id=id")), hasSize(1));
    }

    @Test
    public void activeOnlyFromEnabled() {
        assertThat(Mapping.validate(List.of("userName=username", "active=enabled")), empty());
        assertThat(Mapping.validate(List.of("userName=username", "active=const:true")), hasSize(1));
    }

    @Test
    public void equalsInsideAFilterOrAConstantIsNotTheSeparator() {
        Mapping m = Mapping.parse(
                List.of("userName=username", "emails[type eq \"a=b\"].value=email", "title=const:x=y"));
        assertThat(m.lines(), hasItems("emails[type eq \"a=b\"].value=email", "title=const:x=y"));
    }

    @Test
    public void attrOnAMultiValuedAttributeUsesTheSortedSmallestValue() {
        // The rule is accepted and resolves to the smallest value in code-point order.
        Mapping m = Mapping.parse(List.of("userName=username", "title=attr:dept"));
        TestSubject s = TestSubject.of("1", "u", null, null, null)
                .withAttributes(Map.of("dept", List.of("b", "B", "a", " ")));
        assertThat(m.rules(), hasSize(2));
        assertEquals("B", UserResourceBuilder.build(s, m, true).get("title").asText());
    }

    @Test
    public void rulesAreInCodePointOrder() {
        Mapping m = Mapping.parse(List.of("userName=username", "Zeta=const:z", "alpha=const:a", "Beta=const:b"));
        assertThat(m.rules().stream().map(r -> r.path().attribute()).toList(),
                contains("Beta", "Zeta", "alpha", "userName"));
    }
}
