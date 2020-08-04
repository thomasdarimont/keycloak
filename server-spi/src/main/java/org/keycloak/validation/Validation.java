package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;

/**
 * A generic Validation interface.
 */
@FunctionalInterface
public interface Validation {

    /**
     * Validates the given value in the current {@link NestedValidationContext}.
     * Detailed validation problems can be reported via the {@link ValidationProblem} list.
     *
     * @param key      key of the attribute to validate
     * @param value    the value to validate
     * @param context  the {@link ValidationContext}
     * @return {@literal true} if the validation succeeded, {@literal false} otherwise.
     */
    boolean validate(ValidationKey key, Object value, NestedValidationContext context);

    /**
     * Tells if the validation is supported in the given {@link ValidationContext} for the given {@code value}.
     *
     * @param key
     * @param validationContext
     * @param value
     * @return
     */
    default boolean isSupported(ValidationKey key, Object value, ValidationContext validationContext) {
        return true;
    }

    /**
     * Function interface to check if the current {@link Validation} is supported in the given {@link ValidationContext}.
     *
     * @see #isSupported(ValidationKey, Object, ValidationContext)
     */
    @FunctionalInterface
    interface ValidationSupported {

        ValidationSupported ALWAYS = (k, v, c) -> true;

        /**
         * @param key
         * @param value
         * @param validationContext
         * @return
         */
        boolean test(ValidationKey key, Object value, ValidationContext validationContext);
    }
}
