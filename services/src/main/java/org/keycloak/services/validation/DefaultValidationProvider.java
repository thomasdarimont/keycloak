package org.keycloak.services.validation;

import org.keycloak.services.messages.Messages;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext.ValidationContextKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationRegistry;

import static org.keycloak.validation.ValidationContext.ValidationTarget.User;

public class DefaultValidationProvider implements ValidationProvider {

    // Note: double order constants ease adding new validations before or after a validation.
    // existing validations can also be replaced if the order of a new Validation is equal to the order of the existing
    // Validation for the same validation key.
    public static final double VALIDATION_ORDER_USER_USERNAME = 1000.0;
    public static final double VALIDATION_ORDER_USER_EMAIL = 1100.0;
    public static final double VALIDATION_ORDER_USER_FIRSTNAME = 1200.0;
    public static final double VALIDATION_ORDER_USER_LASTNAME = 1300.0;

    @Override
    public void register(ValidationRegistry validationRegistry) {

        // TODO add additional validators

        validationRegistry.register(User.USERNAME, createUsernameValidation(), VALIDATION_ORDER_USER_USERNAME,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        validationRegistry.register(User.EMAIL, createEmailValidation(), VALIDATION_ORDER_USER_EMAIL,
                ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);

        // TODO firstname / lastname validation could be merged?
        validationRegistry.register(User.FIRSTNAME, createFirstnameValidation(), VALIDATION_ORDER_USER_FIRSTNAME,
                ValidationContextKey.PROFILE_UPDATE);

        validationRegistry.register(User.LASTNAME, createLastnameValidation(), VALIDATION_ORDER_USER_LASTNAME,
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
