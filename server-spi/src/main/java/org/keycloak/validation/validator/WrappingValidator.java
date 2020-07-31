package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

public class WrappingValidator implements Validator {

    private final Validator delegate;

    public WrappingValidator(Validator delegate) {
        this.delegate = delegate;
    }

    @Override
    public ValidationResult validate(String key, Object value, ValidationContext context) {
        return this.delegate.validate(key, value, context);
    }

    public static <V> WrappingValidator of(ValidatorFunc<V> func) {

        Validator validatorAdapter = (key, value, context) -> {
            List<ValidationProblem> problems = new ArrayList<>();
            boolean valid = func.validate(context, key, (V) value, problems);
            if (valid) {
                return ValidationResult.OK;
            }
            return new ValidationResult(false, problems);
        };

        return new WrappingValidator(validatorAdapter);
    }

    public boolean isEnabled(ValidationContext validationContext) {
        return true;
    }

    public boolean isSupported(ValidationContext validationContext) {
        return true;
    }
}
