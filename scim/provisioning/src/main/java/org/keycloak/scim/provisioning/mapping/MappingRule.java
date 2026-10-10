package org.keycloak.scim.provisioning.mapping;

import java.util.Objects;

/** One {@code path=field} line of a mapping. */
public record MappingRule(AttributePath path, FieldRef source) {
    public MappingRule {
        Objects.requireNonNull(path);
        Objects.requireNonNull(source);
    }

    @Override
    public String toString() {
        return path.canonical() + "=" + source.spelling();
    }
}
