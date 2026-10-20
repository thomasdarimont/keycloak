package org.keycloak.scim.provisioning.mapping;

import java.util.List;

/** A mapping that cannot be used. Carries every problem found, not just the first. */
public final class MappingException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public MappingException(String problem) {
        this(List.of(problem));
    }

    public MappingException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
