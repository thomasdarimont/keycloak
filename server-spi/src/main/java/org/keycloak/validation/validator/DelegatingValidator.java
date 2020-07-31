package org.keycloak.validation.validator;

import org.keycloak.models.KeycloakSession;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;

import java.util.List;
import java.util.function.Predicate;

public class DelegatingValidator<V> implements Validator<V> {

    private final Validator<V> delegate;

    private final Predicate<ValidationContext> enabledCheck;

    private final Predicate<ValidationContext> supportedCheck;

    public DelegatingValidator(Validator<V> delegate) {
        this(delegate, c -> true, c -> true);
    }

    public DelegatingValidator(Validator<V> delegate, Predicate<ValidationContext> enabledCheck, Predicate<ValidationContext> supportedCheck) {
        this.delegate = delegate;
        this.enabledCheck = enabledCheck;
        this.supportedCheck = supportedCheck;
    }

    @Override
    public boolean validate(String key, V value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session) {
        return this.delegate.validate(key, value, context, problems, session);
    }

    public boolean isEnabled(ValidationContext validationContext) {
        return enabledCheck.test(validationContext);
    }

    public boolean isSupported(ValidationContext validationContext) {
        return supportedCheck.test(validationContext);
    }
}
