package org.keycloak.validation;

import java.util.List;

/**
 * A registry for {@link Validation}s.
 */
public interface ValidationRegistry {

    /**
     * Returns all {@link Validation}s, that are eligible for the given key and {@link ValidationContext}.
     *
     * @param context
     * @param key
     * @return
     */
    List<Validation<?>> getValidations(ValidationContext context, String key);

    void register(String key, Validation<?> validation, double order, String... contextKeys);
}
