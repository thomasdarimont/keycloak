package org.keycloak.services.validation.validators;

import org.keycloak.models.UserModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.validator.DelegatingValidator;
import org.keycloak.validation.validator.ValidatorProvider;
import org.keycloak.validation.validator.ValidatorRegistry;

import javax.ws.rs.core.MultivaluedMap;

public class DefaultValidatorProvider implements ValidatorProvider {

    @Override
    public void register(ValidatorRegistry validatorRegistry) {

        DelegatingValidator emailValidator = DelegatingValidator.forNamedProperty(Validation.FIELD_EMAIL, UserModel.class, this::extractEmail, (context, key, value, problems) -> {

            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_EMAIL));
                return false;
            }

            if (!Validation.isEmailValid(value)) {
                problems.add(ValidationProblem.error(key, Messages.INVALID_EMAIL));
                return false;
            }

            return true;
        }, 1010.0);

        DelegatingValidator usernameValidator = DelegatingValidator.forNamedProperty(Validation.FIELD_USERNAME, UserModel.class, this::extractUsername, (context, key, value, problems) -> {
            if (!context.getRealm().isRegistrationEmailAsUsername() && context.getAttributeBoolean("userNameRequired") && Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_USERNAME));
                return false;
            }
            return true;
        }, 1000.0);

        DelegatingValidator firstnameValidator = DelegatingValidator.forNamedProperty(Validation.FIELD_FIRST_NAME, UserModel.class, this::extractFirstname, (context, key, value, problems) -> {
            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_FIRST_NAME));
                return false;
            }
            return true;
        }, 1000.0);

        DelegatingValidator lastnameValidator = DelegatingValidator.forNamedProperty(Validation.FIELD_LAST_NAME, UserModel.class, this::extractLastname, (context, key, value, problems) -> {
            if (Validation.isBlank(value)) {
                problems.add(ValidationProblem.error(key, Messages.MISSING_LAST_NAME));
                return false;
            }
            return true;
        }, 1000.0);

        // TODO add additional validators

        validatorRegistry.register(UserModel.class, firstnameValidator);
        validatorRegistry.register(UserModel.class, lastnameValidator);
        validatorRegistry.register(UserModel.class, emailValidator);
        validatorRegistry.register(UserModel.class, usernameValidator);
    }

    protected String extractEmail(Object o) {
        if (o instanceof UserModel) {
            return ((UserModel) o).getEmail();
        } else if (o instanceof MultivaluedMap) { // Form Posts
            return ((MultivaluedMap<String, String>) o).getFirst(Validation.FIELD_EMAIL);
        }
        return null;
    }

    protected String extractUsername(Object o) {
        if (o instanceof UserModel) {
            return ((UserModel) o).getUsername();
        } else if (o instanceof MultivaluedMap) { // Form Posts
            return ((MultivaluedMap<String, String>) o).getFirst(Validation.FIELD_USERNAME);
        }
        return null;
    }

    protected String extractLastname(Object o) {
        if (o instanceof UserModel) {
            return ((UserModel) o).getLastName();
        } else if (o instanceof MultivaluedMap) { // Form Posts
            return ((MultivaluedMap<String, String>) o).getFirst(Validation.FIELD_LAST_NAME);
        }
        return null;
    }


    protected String extractFirstname(Object o) {
        if (o instanceof UserModel) {
            return ((UserModel) o).getFirstName();
        } else if (o instanceof MultivaluedMap) { // Form Posts
            return ((MultivaluedMap<String, String>) o).getFirst(Validation.FIELD_FIRST_NAME);
        }
        return null;
    }
}
