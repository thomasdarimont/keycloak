package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationResult;

public interface Validator {

    ValidationResult validate(String key, Object value, ValidationContext context);

    default boolean isEnabled(ValidationContext validationContext) {
        return true;
    }

    default boolean isSupported(ValidationContext validationContext) {
        return true;
    }
}
