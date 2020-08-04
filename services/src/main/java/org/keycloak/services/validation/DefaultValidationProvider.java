/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.services.validation;

import org.keycloak.services.messages.Messages;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContextKey;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationKey;

public class DefaultValidationProvider implements ValidationProvider {

    // Note: double order constants ease adding new validations before or after a validation.
    // existing validations can also be replaced if the order of a new Validation is equal to the order of the existing
    // Validation for the same validation key.

    // TODO move orders to appropriate ValidationKey enum
    public static final double VALIDATION_ORDER_USER_USERNAME = 1000.0;
    public static final double VALIDATION_ORDER_USER_EMAIL = 1100.0;
    public static final double VALIDATION_ORDER_USER_FIRSTNAME = 1200.0;
    public static final double VALIDATION_ORDER_USER_LASTNAME = 1300.0;

    @Override
    public void register(ValidationRegistry validationRegistry) {

        // TODO add additional validators

        validationRegistry.registerValidation(createUsernameValidation(), ValidationKey.USER_USERNAME, VALIDATION_ORDER_USER_USERNAME,
                ValidationContextKey.USER_PROFILE_UPDATE, ValidationContextKey.USER_REGISTRATION);

        validationRegistry.registerValidation(createEmailValidation(), ValidationKey.USER_EMAIL, VALIDATION_ORDER_USER_EMAIL,
                ValidationContextKey.USER_PROFILE_UPDATE, ValidationContextKey.USER_REGISTRATION);

        // TODO firstname / lastname validation could be merged?
        validationRegistry.registerValidation(createFirstnameValidation(), ValidationKey.USER_FIRSTNAME, VALIDATION_ORDER_USER_FIRSTNAME,
                ValidationContextKey.USER_PROFILE_UPDATE);

        validationRegistry.registerValidation(createLastnameValidation(), ValidationKey.USER_LASTNAME, VALIDATION_ORDER_USER_LASTNAME,
                ValidationContextKey.USER_PROFILE_UPDATE);
    }

    protected Validation createLastnameValidation() {
        return (key, value, context) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                context.addError(key, Messages.MISSING_LAST_NAME);
                return false;
            }
            return true;
        };
    }

    protected Validation createFirstnameValidation() {
        return (key, value, context) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                context.addError(key, Messages.MISSING_FIRST_NAME);
                return false;
            }
            return true;
        };
    }

    protected Validation createEmailValidation() {
        return (key, value, context) -> {

            String input = value instanceof String ? (String) value : null;

            if (org.keycloak.services.validation.Validation.isBlank(input)) {
                context.addError(key, Messages.MISSING_EMAIL);
                return false;
            }

            if (!org.keycloak.services.validation.Validation.isEmailValid(input)) {
                context.addError(key, Messages.INVALID_EMAIL);
                return false;
            }

            return true;
        };
    }

    protected Validation createUsernameValidation() {
        return (key, value, context) -> {

            String input = value instanceof String ? (String) value : null;

            if (!context.getRealm().isRegistrationEmailAsUsername()
                    && context.getAttributeAsBoolean("userNameRequired")
                    && org.keycloak.services.validation.Validation.isBlank(input)) {
                context.addError(key, Messages.MISSING_USERNAME);
                return false;
            }
            return true;
        };
    }
}
