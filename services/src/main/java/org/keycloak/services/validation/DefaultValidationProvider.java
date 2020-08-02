package org.keycloak.services.validation;

import org.keycloak.services.messages.Messages;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext.ValidationContextKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationRegistry;

import static org.keycloak.validation.ValidationContext.ValidationTarget.User;

public class DefaultValidationProvider implements ValidationProvider {

    @Override
    public void register(ValidationRegistry validationRegistry) {

        // TODO add additional validators

        validationRegistry.register(User.USERNAME, createUsernameValidation(), 1000.0,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        validationRegistry.register(User.EMAIL, createEmailValidation(), 1100.0,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        validationRegistry.register(User.FIRSTNAME, createFirstnameValidation(), 1200.0,
                ValidationContextKey.PROFILE_UPDATE);

        validationRegistry.register(User.LASTNAME, createLastnameValidation(), 1300.0,
                ValidationContextKey.PROFILE_UPDATE);
    }

    protected Validation createLastnameValidation() {
        return (key, value, context, problems, session) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_LAST_NAME));
                return false;
            }
            return true;
        };
    }

    protected Validation createFirstnameValidation() {
        return (key, value, context, problems, session) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_FIRST_NAME));
                return false;
            }
            return true;
        };
    }

    protected Validation createEmailValidation() {
        return (key, value, context, problems, session) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_EMAIL));
                return false;
            }

            if (!org.keycloak.services.validation.Validation.isEmailValid(input)) {
                problems.add(ValidationProblem.error(key, Messages.INVALID_EMAIL));
                return false;
            }

            return true;
        };
    }

    protected Validation createUsernameValidation() {
        return (key, value, context, problems, session) -> {

            String input = value instanceof String ? (String) value : null;

            if (!context.getRealm().isRegistrationEmailAsUsername()
                    && context.getAttributeBoolean("userNameRequired")
                    && org.keycloak.services.validation.Validation.isBlank(input)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_USERNAME));
                return false;
            }
            return true;
        };
    }
}
