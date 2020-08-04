package org.keycloak.services.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.NestedValidationContext;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.ValidatorProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultValidatorProvider implements ValidatorProvider {

    private final KeycloakSession session;
    private final ValidationRegistry validationRegistry;

    public DefaultValidatorProvider(KeycloakSession session, ValidationRegistry validationRegistry) {
        this.session = session;
        this.validationRegistry = validationRegistry;
    }

    @Override
    public ValidationResult validate(ValidationContext context, Object value, Set<ValidationKey> keys) {

        Map<ValidationKey, List<Validation>> validators = validationRegistry.resolveValidations(context, keys, value);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        NestedValidationContext nestedContext = new NestedValidationContext(context, session);
        boolean valid = validateInternal(nestedContext, value, validators);
        List<ValidationProblem> problems = nestedContext.getProblems();

        return new ValidationResult(valid, problems);
    }

    protected boolean validateInternal(NestedValidationContext context, Object value, Map<ValidationKey, List<Validation>> validators) {

        boolean valid = true;
        for (Map.Entry<ValidationKey, List<Validation>> entry : validators.entrySet()) {
            for (Validation validation : entry.getValue()) {
                // TODO add support for early exit short-circuit validation via flag in ValidationContext
                valid &= validation.validate(entry.getKey(), value, context);
            }
        }
        return valid;
    }
}
