package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;

import java.util.List;

/**
 * A generic Validation interface.
 * @param <V>
 */
public interface Validation<V> {

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
    boolean validate(String key, V value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session);

    /**
     * Tells if the validation is enabled in the given {@link ValidationContext}.
     * @param validationContext
     * @return
     */
    default boolean isEnabled(ValidationContext validationContext) {
        return true;
    }

    /**
     * Tells if the validation is supported in the given {@link ValidationContext}.
     *
     * @param validationContext
     * @return
     */
    default boolean isSupported(ValidationContext validationContext) {
        return true;
    }
}
