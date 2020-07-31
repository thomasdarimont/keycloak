package org.keycloak.services.validation;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.validator.Validator;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultValidationProvider implements ValidationProvider {

    private final ValidatorRegistry validatorRegistry;

    public DefaultValidationProvider(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public <T> ValidationResult validate(String key, T target, ValidationContext context) {

        List<Validator> validators = filterValidators(validatorRegistry.getValidators(context), key);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        ValidationResult result = ValidationResult.OK;

        for (Validator validator : validators) {
            ValidationResult current = validator.validate(key, target, context);
            result = new ValidationResult(result, current);
        }

        return result;
    }

    private List<Validator> filterValidators(List<Validator> validators, String key) {
        if (validators == null || validators.isEmpty()) {
            return null;
        }

        return validators.stream().filter(v -> v.getKey().equals(key)).collect(Collectors.toList());
    }
}
