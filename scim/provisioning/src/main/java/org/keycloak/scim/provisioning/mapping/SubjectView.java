package org.keycloak.scim.provisioning.mapping;

import java.util.List;

/**
 * The slice of a user that a mapping can read. This is the only user abstraction in core and it
 * exists for testability, not as a host abstraction.
 */
public interface SubjectView {
    String id();

    String username();

    String email();

    String firstName();

    String lastName();

    boolean enabled();

    /** Every value of a user attribute, in any order; empty when the attribute is absent. */
    List<String> attribute(String name);
}
