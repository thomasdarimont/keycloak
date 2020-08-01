package org.keycloak.validation;

import org.keycloak.provider.Provider;

public interface ValidationProvider extends Provider {

    /**
     * Registers new {@link Validation} implementations into the given {@link ValidationRegistry}.
     * @param validationRegistry
     */
    void register(ValidationRegistry validationRegistry);

    default void close() {
        // NOOP
    }
}
