package org.keycloak.services.validation.validators;

import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.validation.ValidationContext.ValidationContextKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.validator.ValidatorProvider;
import org.keycloak.validation.validator.ValidatorRegistry;
import org.keycloak.validation.validator.WrappingValidator;

import static org.keycloak.validation.ValidationContext.ValidationTarget.User;

public class DefaultValidatorProvider implements ValidatorProvider {

    @Override
    public void register(ValidatorRegistry validatorRegistry) {

        // TODO add additional validators

        validatorRegistry.register(User.USERNAME, createUsernameValidator(), 1000.0, ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);
        validatorRegistry.register(User.EMAIL, createEmailValidator(), 1100.0, ValidationContextKey.PROFILE_UPDATE, ValidationContextKey.REGISTRATION);
        validatorRegistry.register(User.FIRSTNAME, createFirstnameValidator(), 1200.0, ValidationContextKey.PROFILE_UPDATE);
        validatorRegistry.register(User.LASTNAME, createLastnameValidator(), 1300.0, ValidationContextKey.PROFILE_UPDATE);
    }

    protected WrappingValidator createLastnameValidator() {
        return WrappingValidator.<String>of((context, key, value, problems) -> {
            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_LAST_NAME));
                return false;
            }
            return true;
        });
    }

    protected WrappingValidator createFirstnameValidator() {
        return WrappingValidator.<String>of((context, key, value, problems) -> {
            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_FIRST_NAME));
                return false;
            }
            return true;
        });
    }

    protected WrappingValidator createEmailValidator() {
        return WrappingValidator.<String>of((context, key, value, problems) -> {

            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_EMAIL));
                return false;
            }

            if (!Validation.isEmailValid(value)) {
                problems.add(ValidationProblem.error(key, Messages.INVALID_EMAIL));
                return false;
            }

            return true;
        });
    }

    protected WrappingValidator createUsernameValidator() {
        return WrappingValidator.<String>of((context, key, value, problems) -> {
            if (!context.getRealm().isRegistrationEmailAsUsername()
                    && context.getAttributeBoolean("userNameRequired")
                    && Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_USERNAME));
                return false;
            }
            return true;
        });
    }
}
