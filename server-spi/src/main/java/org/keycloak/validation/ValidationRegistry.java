package org.keycloak.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A registry for {@link Validation Validation's}.
 */
public interface ValidationRegistry {

    /**
     * Returns all {@link Validation Validation's} registered for the given Set of validation keys.
     *
     * @param keys
     * @return Map with validation key as key and the List of associated {@link Validation Validation's}.
     */
    Map<ValidationKey, List<Validation>> getValidations(Set<ValidationKey> keys);

    /**
     * Returns all {@link Validation Validation's} registered for the given validation key.
     *
     * @param key
     * @return
     */
    List<Validation> getValidations(ValidationKey key);

    /**
     * Returns all {@link Validation}s, that are eligible for the given validation context keys
     * and {@link ValidationContext} as well as the given value.
     *
     * @param context
     * @param keys
     * @param value
     * @return Map with validation key as key and the List of associated {@link Validation Validation's}.
     */
    Map<ValidationKey, List<Validation>> resolveValidations(ValidationContext context, Set<ValidationKey> keys, Object value);

    /**
     * Returns all {@link Validation}s, that are eligible for the given validation context key
     * and {@link ValidationContext} as well as the given value.
     * @param context
     * @param key
     * @param value
     * @return
     */
    List<Validation> resolveValidations(ValidationContext context, ValidationKey key, Object value);

    /**
     * Registers a new {@link Validation} for the given validation key that can be applied in the given validation context keys.
     *
     * @param validation
     * @param key
     * @param order
     * @param contextKeys
     */
    void registerValidation(Validation validation, ValidationKey key, double order, Set<ValidationContextKey> contextKeys);

    default void registerValidation(Validation validation, ValidationKey key, double order, ValidationContextKey... contextKeys) {
        registerValidation(validation, key, order, new LinkedHashSet<>(Arrays.asList(contextKeys)));
    }
}
