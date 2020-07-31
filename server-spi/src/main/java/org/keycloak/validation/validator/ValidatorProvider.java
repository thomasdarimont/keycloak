package org.keycloak.validation.validator;

import org.keycloak.provider.Provider;

public interface ValidatorProvider extends Provider {

    /**
     * Registers new {@link Validator} implementations into the given {@link ValidatorRegistry}.
     * @param validatorRegistry
     */
    void register(ValidatorRegistry validatorRegistry);

    default void close() {
        // NOOP
    }
}
