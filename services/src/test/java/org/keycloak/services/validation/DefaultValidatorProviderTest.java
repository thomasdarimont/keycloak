package org.keycloak.services.validation;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.validation.NestedValidationContext;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationContextKey;
import org.keycloak.validation.ValidationKey;
import org.keycloak.validation.ValidationKey.CustomValidationKey;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidationResult;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DefaultValidatorProviderTest {

    DefaultValidatorProvider validator;
    ValidationRegistry registry;

    KeycloakSession session;

    RealmModel realm;

    @Before
    public void setup() {

        // TODO configure realm mock

        registry = new DefaultValidationRegistry();
        validator = new DefaultValidatorProvider(session, registry);
    }

    @Test
    public void validateEmail() {

        new DefaultValidationProvider().register(registry);

        ValidationContext context = new ValidationContext(realm, ValidationContextKey.User.PROFILE_UPDATE);

        ValidationResult result;
        ValidationProblem problem;

        result = validator.validate(context, "test@localhost", ValidationKey.User.EMAIL);
        assertTrue("A valid email should be valid", result.isValid());
        assertFalse("A valid email should cause no problems", result.hasProblems());

        result = validator.validate(context, "", ValidationKey.User.EMAIL);
        assertFalse("An empty email should be invalid", result.isValid());
        assertTrue("An empty email should cause report problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.EMAIL).get(0);
        assertEquals("An empty email should result in a problem", Messages.MISSING_EMAIL, problem.getMessage());
        assertTrue("An empty email should result in a missing email error", problem.isError());

        result = validator.validate(context, null, ValidationKey.User.EMAIL);
        assertFalse("A null email should be invalid", result.isValid());
        assertTrue("A null email should cause report problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.EMAIL).get(0);
        assertEquals("An null email should result in a missing email problem", Messages.MISSING_EMAIL, problem.getMessage());

        result = validator.validate(context, "invalid", ValidationKey.User.EMAIL);
        assertFalse("A null email should be invalid", result.isValid());
        assertTrue("A null email should cause report problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.EMAIL).get(0);
        assertEquals("An null email should result in a problem", Messages.INVALID_EMAIL, problem.getMessage());
        assertTrue("An null email should result in a invalid email error", problem.isError());
    }

    @Test
    public void validateMultipleFieldsInValidationContext() {

        new DefaultValidationProvider().register(registry);

        ValidationContext context = new ValidationContext(realm, ValidationContextKey.User.PROFILE_UPDATE);

        ValidationResult result;
        ValidationProblem problem;

        result = validator.validate(context, "test@localhost", ValidationKey.User.EMAIL);
        assertTrue("A valid email should be valid", result.isValid());
        assertFalse("A valid email should cause no problems", result.hasProblems());

        result = validator.validate(context, "Theo", ValidationKey.User.FIRSTNAME);
        assertTrue("A valid firstname should be valid", result.isValid());
        assertFalse("A valid firstname should cause no problems", result.hasProblems());

        result = validator.validate(context, null, ValidationKey.User.LASTNAME);
        assertFalse("An invalid lastname should be valid", result.isValid());
        assertTrue("An invalid lastname should cause no problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.LASTNAME).get(0);
        assertEquals("An invalid lastname should result in a problem", Messages.MISSING_LAST_NAME, problem.getMessage());
        assertTrue("An invalid lastname should result in a missing lastname error", problem.isError());
    }

    @Test
    public void validateWithCustomValidation() {

        new DefaultValidationProvider().register(registry);

        registry.register(CustomValidations::validatePhone, CustomValidations.PHONE, ValidationRegistry.DEFAULT_ORDER, ValidationContextKey.User.PROFILE_UPDATE);

        ValidationContext context = new ValidationContext(realm, ValidationContextKey.User.PROFILE_UPDATE);

        ValidationResult result;
        ValidationProblem problem;

        result = validator.validate(context, "+4912345678", CustomValidations.PHONE);
        assertTrue("A valid phone number should be valid", result.isValid());
        assertFalse("A valid phone number should cause no problems", result.hasProblems());

        result = validator.validate(context, "", CustomValidations.PHONE);
        assertFalse("A missing phone number should be invalid", result.isValid());
        assertTrue("A missing phone should cause problems", result.hasProblems());
        problem = result.getErrors(CustomValidations.PHONE).get(0);
        assertEquals("A missing phone should result in a problem", CustomValidations.MISSING_PHONE, problem.getMessage());
        assertTrue("A missing phone should result in a missing email error", problem.isError());
    }

    @Test
    public void validateWithCustomValidationForBuiltInValidation() {

        new DefaultValidationProvider().register(registry);

        registry.register(CustomValidations::validateEmailCustom, ValidationKey.User.EMAIL,
                ValidationRegistry.DEFAULT_ORDER + 1000.0, ValidationContextKey.User.REGISTRATION);

        ValidationContext context = new ValidationContext(realm, ValidationContextKey.User.REGISTRATION);

        ValidationResult result;
        ValidationProblem problem;

        result = validator.validate(context, "test@allowed", ValidationKey.User.EMAIL);
        assertTrue("A valid email should be valid", result.isValid());
        assertFalse("A valid email should cause no problems", result.hasProblems());

        result = validator.validate(context, "test@notallowed", ValidationKey.User.EMAIL);
        assertFalse("A not allowed email should be invalid", result.isValid());
        assertTrue("A not allowed email should cause report problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.EMAIL).get(0);
        assertEquals("A not allowed should result in a problem", CustomValidations.EMAIL_NOT_ALLOWED, problem.getMessage());
        assertTrue("A not allowed should result in a email not allowed error", problem.isError());
    }

    @Test
    public void validateWithReplacedDefaultValidation() {

        new DefaultValidationProvider().register(registry);

        // default order = 0.0 effectively replaces the existing validator
        org.keycloak.validation.Validation customValidation = CustomValidations::validateEmailCustom;
        registry.register(customValidation, ValidationKey.User.EMAIL,
                ValidationRegistry.DEFAULT_ORDER, ValidationContextKey.User.REGISTRATION);

        List<org.keycloak.validation.Validation> validations = registry.getValidations(ValidationKey.User.EMAIL);
        assertEquals("Should have only one validation", 1, validations.size());
        assertSame(customValidation, validations.get(0));

        ValidationContext context = new ValidationContext(realm, ValidationContextKey.User.REGISTRATION);

        ValidationResult result;
        ValidationProblem problem;

        result = validator.validate(context, "test@allowed", ValidationKey.User.EMAIL);
        assertTrue("A valid email should be valid", result.isValid());
        assertFalse("A valid email should cause no problems", result.hasProblems());

        result = validator.validate(context, "test@notallowed", ValidationKey.User.EMAIL);
        assertFalse("A not allowed email should be invalid", result.isValid());
        assertTrue("A not allowed email should cause report problems", result.hasProblems());
        problem = result.getErrors(ValidationKey.User.EMAIL).get(0);
        assertEquals("A not allowed should result in a problem", CustomValidations.EMAIL_NOT_ALLOWED, problem.getMessage());
        assertTrue("A not allowed should result in a email not allowed error", problem.isError());
    }

    interface CustomValidations {

        String MISSING_PHONE = "missing_phone";

        String EMAIL_NOT_ALLOWED = "invalid_email_not_allowed";

        CustomValidationKey PHONE = ValidationKey.newCustomKey("user.phone");

        static boolean validatePhone(ValidationKey key, Object value, NestedValidationContext context) {

            String input = value instanceof String ? (String) value : null;

            if (Validation.isBlank(input)) {
                context.addError(key, MISSING_PHONE);
                return false;
            }

            return true;
        }

        static boolean validateEmailCustom(ValidationKey key, Object value, NestedValidationContext context) {

            String input = value instanceof String ? (String) value : null;

            if (input == null || !input.endsWith("@allowed")) {
                context.addError(key, EMAIL_NOT_ALLOWED);
                return false;
            }

            return true;
        }
    }

}