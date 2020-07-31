package org.keycloak.services.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.validator.Validator;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.ArrayList;
import java.util.List;

public class DefaultValidationProvider implements ValidationProvider {

    private final KeycloakSession session;
    private final ValidatorRegistry validatorRegistry;

    public DefaultValidationProvider(KeycloakSession session, ValidatorRegistry validatorRegistry) {
        this.session = session;
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public <V> ValidationResult validate(String key, V value, ValidationContext context) {

        List<Validator<?>> validators = validatorRegistry.getValidators(context, key);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        List<ValidationProblem> problems = new ArrayList<>();
        boolean valid = true;
        for (Validator validator : validators) {
            valid &= validator.validate(key, value, context, problems, session);

        }
        return new ValidationResult(valid, problems);
    }
}
