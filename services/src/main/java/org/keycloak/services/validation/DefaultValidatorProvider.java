package org.keycloak.services.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.NestedValidationContext;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.ValidatorProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultValidatorProvider implements ValidatorProvider {

    private final KeycloakSession session;
    private final ValidationRegistry validatorRegistry;

    public DefaultValidatorProvider(KeycloakSession session, ValidationRegistry validatorRegistry) {
        this.session = session;
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public ValidationResult validate(ValidationContext context, Object value, Set<String> keys) {

        Map<String, List<Validation>> validators = validatorRegistry.getValidations(context, keys, value);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        NestedValidationContext nestedContext = new NestedValidationContext(context, session);
        boolean valid = validateInternal(nestedContext, value, validators);
        return new ValidationResult(valid, nestedContext.getProblems());
    }

    protected boolean validateInternal(NestedValidationContext context, Object value, Map<String, List<Validation>> validators) {

        boolean valid = true;
        for (Map.Entry<String, List<Validation>> entry : validators.entrySet()) {
            for (Validation validation : entry.getValue()) {
                // TODO add support for early exit short-circuit validation via flag in ValidationContext
                valid &= validation.validate(entry.getKey(), value, context);
            }
        }
        return valid;
    }
}
