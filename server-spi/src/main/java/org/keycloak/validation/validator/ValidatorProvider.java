package org.keycloak.validation.validator;

import org.keycloak.provider.Provider;

public interface ValidatorProvider extends Provider {

    void register(ValidatorRegistry validatorRegistry);

    default void close() {
        // NOOP
    }
}
