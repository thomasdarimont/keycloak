package org.keycloak.validation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A registry for {@link Validation}s.
 */
public interface ValidationRegistry {

    /**
     * Returns all {@link Validation}s, that are eligible for the given keys and {@link ValidationContext}.
     *
     * @param context
     * @param keys
     * @return
     */
    Map<String, List<Validation<?>>> getValidations(ValidationContext context, Set<String> keys);

    /**
     * Registers a new {@link Validation} for the given validation key that can be applied in the given validation context keys.
     *
     * @param key
     * @param validation
     * @param order
     * @param contextKeys
     */
    void register(String key, Validation<?> validation, double order, Set<String> contextKeys);

    default void register(String key, Validation<?> validation, double order, String... contextKeys) {
        register(key, validation, order, new LinkedHashSet<>(Arrays.asList(contextKeys)));
    }
}
