package org.keycloak.services.validation;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.validator.Validator;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.List;

public class DefaultValidationProvider implements ValidationProvider {

    private final ValidatorRegistry validatorRegistry;

    public DefaultValidationProvider(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public <V> ValidationResult validate(String key, V value, ValidationContext context) {

        List<Validator> validators = validatorRegistry.getValidators(context, key);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        ValidationResult result = ValidationResult.OK;

        for (Validator validator : validators) {
            ValidationResult current = validator.validate(key, value, context);
            result = new ValidationResult(result, current);
        }

        return result;
    }
}
