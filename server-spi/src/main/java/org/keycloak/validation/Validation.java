package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;

/**
 * A generic Validation interface.
 */
@FunctionalInterface
public interface Validation {

    /**
     * Validates the given value in the current {@link ValidationContext}.
     * Detailed validation problems can be reported via the {@link ValidationProblem} list.
     *
     * @param key      key of the attribute to validate
     * @param value    the value to validate
     * @param context  the {@link ValidationContext}
     * @param problems the {@link List} of {@link ValidationProblem ValidationProblem's}
     * @param session  the {@link KeycloakSession}
     * @return {@literal true} if the validation succeeded, {@literal false} otherwise.
     */
    boolean validate(String key, Object value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session);

    /**
     * Tells if the validation is enabled in the given {@link ValidationContext}.
     *
     * @param key
     * @param validationContext
     * @return
     */
    default boolean isEnabled(String key, ValidationContext validationContext) {
        return true;
    }

    /**
     * Tells if the validation is supported in the given {@link ValidationContext} for the given {@code value}.
     *
     * @param key
     * @param validationContext
     * @param value
     * @return
     */
    default boolean isSupported(String key, Object value, ValidationContext validationContext) {
        return true;
    }

    /**
     * Function interface to check if the current {@link Validation} is supported in the given {@link ValidationContext}.
     *
     * @see #isSupported(String, Object, ValidationContext)
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
        boolean test(String key, Object value, ValidationContext validationContext);
    }

    /**
     * Function interface to check if the current {@link Validation} is enabled in the given  {@link ValidationContext}.
     */
    @FunctionalInterface
    interface ValidationEnabled {

        ValidationEnabled ALWAYS = (k, c) -> true;

        /**
         * @param key
         * @param validationContext
         * @return
         */
        boolean test(String key, ValidationContext validationContext);
    }
}
