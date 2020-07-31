package org.keycloak.services.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.ValidatorProvider;

import java.util.ArrayList;
import java.util.List;

public class DefaultValidatorProvider implements ValidatorProvider {

    private final KeycloakSession session;
    private final ValidationRegistry validatorRegistry;

    public DefaultValidatorProvider(KeycloakSession session, ValidationRegistry validatorRegistry) {
        this.session = session;
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> ValidationResult validate(String key, V value, ValidationContext context) {

        List<Validation<?>> validators = validatorRegistry.getValidations(context, key);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        List<ValidationProblem> problems = new ArrayList<>();
        boolean valid = true;

        // TODO fix generics
        for (Validation validator : validators) {
            valid &= validator.validate(key, value, context, problems, session);
        }

        return new ValidationResult(valid, problems);
    }
}
