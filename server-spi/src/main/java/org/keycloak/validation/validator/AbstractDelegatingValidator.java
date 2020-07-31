package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;
import org.keycloak.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractDelegatingValidator extends AbstractValidator {

    private final Validator delegate;

    private final Class<?> type;

    public AbstractDelegatingValidator(Class<?> type, String key, Validator delegate, double order) {
        super(key, order);
        this.delegate = delegate;
        this.type = type;
    }

    public static <T, V> Validator createValidatorAdapter(Function<T, V> extractor, ValueValidation<V> check) {
        return (key, target, context) -> {
            V value = extractor.apply((T)target);
            List<ValidationProblem> problems = new ArrayList<>();
            boolean valid = check.validate(context, key, value, problems);
            if (valid) {
                return ValidationResult.OK;
            }
            return new ValidationResult(false, problems);
        };
    }

    @Override
    public ValidationResult validate(String key, Object target, ValidationContext context) {
        return this.delegate.validate(key, target, context);
    }

    public Class<?> getType() {
        return type;
    }
}
