package org.keycloak.services.validation.validators;

import org.keycloak.services.messages.Messages;
import org.keycloak.validation.ValidationContext.ValidationContextKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationRegistry;

import static org.keycloak.validation.ValidationContext.ValidationTarget.User;

public class DefaultValidationProvider implements ValidationProvider {

    @Override
    public void register(ValidationRegistry validatorRegistry) {

        // TODO add additional validators

        validatorRegistry.register(User.USERNAME, createUsernameValidation(), 1000.0,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        validatorRegistry.register(User.EMAIL, createEmailValidation(), 1100.0,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        validatorRegistry.register(User.FIRSTNAME, createFirstnameValidation(), 1200.0,
                ValidationContextKey.PROFILE_UPDATE);

        validatorRegistry.register(User.LASTNAME, createLastnameValidation(), 1300.0,
                ValidationContextKey.PROFILE_UPDATE);
    }

    protected Validation<String> createLastnameValidation() {
        return (key, value, context, problems, session) -> {
            if (org.keycloak.services.validation.Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_LAST_NAME));
                return false;
            }
            return true;
        };
    }

    protected Validation<String> createFirstnameValidation() {
        return (key, value, context, problems, session) -> {
            if (org.keycloak.services.validation.Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_FIRST_NAME));
                return false;
            }
            return true;
        };
    }

    protected Validation<String> createEmailValidation() {
        return (key, value, context, problems, session) -> {

            if (org.keycloak.services.validation.Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_EMAIL));
                return false;
            }

            if (!org.keycloak.services.validation.Validation.isEmailValid(value)) {
                problems.add(ValidationProblem.error(key, Messages.INVALID_EMAIL));
                return false;
            }

            return true;
        };
    }

    protected Validation<String> createUsernameValidation() {
        return (key, value, context, problems, session) -> {
            if (!context.getRealm().isRegistrationEmailAsUsername()
                    && context.getAttributeBoolean("userNameRequired")
                    && org.keycloak.services.validation.Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_USERNAME));
                return false;
            }
            return true;
        };
    }
}
