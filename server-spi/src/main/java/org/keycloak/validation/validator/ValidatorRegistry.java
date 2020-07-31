package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;

import java.util.List;

/**
 * A registry for Validators.
 */
public interface ValidatorRegistry {

    /**
     * Retruns all validators, that are eligible for the given key and {@link ValidationContext}.
     *
     * @param context
     * @param key
     * @return
     */
    List<Validator<?>> getValidators(ValidationContext context, String key);

    void register(String key, Validator<?> validator, double order, String... contextKeys);
}
