package org.keycloak.scim.provisioning.mapping;

import java.util.List;
import java.util.Map;

/** A plain {@link SubjectView} for tests. */
public record TestSubject(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Map<String, List<String>> attributes) implements SubjectView {

    public static TestSubject of(String id, String username, String email, String first, String last) {
        return new TestSubject(id, username, email, first, last, true, Map.of());
    }

    public TestSubject withAttributes(Map<String, List<String>> attrs) {
        return new TestSubject(id, username, email, firstName, lastName, enabled, attrs);
    }

    public TestSubject disabled() {
        return new TestSubject(id, username, email, firstName, lastName, false, attributes);
    }

    @Override
    public List<String> attribute(String name) {
        return attributes.getOrDefault(name, List.of());
    }
}
