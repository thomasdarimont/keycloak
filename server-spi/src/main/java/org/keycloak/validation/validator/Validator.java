package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;

import java.util.List;

public interface Validator<V> {

    boolean validate(String key, V value, ValidationContext context, List<ValidationProblem> problems);

    default boolean isEnabled(ValidationContext validationContext) {
        return true;
    }

    default boolean isSupported(ValidationContext validationContext) {
        return true;
    }
}
