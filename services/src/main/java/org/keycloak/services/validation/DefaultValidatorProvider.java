package org.keycloak.services.validation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationResult;
import org.keycloak.validation.ValidatorProvider;

import javax.xml.validation.Validator;
import java.util.ArrayList;
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
    @SuppressWarnings({"unchecked"})
    public <V> ValidationResult validate(ValidationContext context, V value, Set<String> keys) {

        // TODO fix generics
        Map<String, List<Validation<V>>> validators = (Map<String, List<Validation<V>>>)(Object)validatorRegistry.getValidations(context, keys);

        if (validators == null || validators.isEmpty()) {
            return null;
        }

        List<ValidationProblem> problems = new ArrayList<>();
        boolean valid = true;

        for(Map.Entry<String, List<Validation<V>>> entry: validators.entrySet()) {
            for (Validation<V> validation : entry.getValue()) {
                valid &= validation.validate(entry.getKey(), value, context, problems, session);
            }
        }

        return new ValidationResult(valid, problems);
    }
}
