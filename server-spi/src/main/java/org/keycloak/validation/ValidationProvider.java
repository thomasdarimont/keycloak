package org.keycloak.validation;

import org.keycloak.provider.Provider;

/**
 * Provides custom {@link Validation Validation's} to a given {@link ValidationRegistry}.
 */
public interface ValidationProvider extends Provider {

    /**
     * Registers new {@link Validation} implementations into the given {@link ValidationRegistry}.
     *
     * @param validationRegistry to store the new {@link Validation Validation's}.
     */
    void register(ValidationRegistry validationRegistry);

    default void close() {
        // NOOP
    }
}
