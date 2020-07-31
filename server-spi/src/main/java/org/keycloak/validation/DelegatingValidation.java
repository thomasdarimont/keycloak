package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;
import java.util.function.Predicate;

public class DelegatingValidation<V> implements Validation<V> {

    private final Validation<V> delegate;

    private final Predicate<ValidationContext> enabledCheck;

    private final Predicate<ValidationContext> supportedCheck;

    public DelegatingValidation(Validation<V> delegate) {
        this(delegate, c -> true, c -> true);
    }

    public DelegatingValidation(Validation<V> delegate, Predicate<ValidationContext> enabledCheck, Predicate<ValidationContext> supportedCheck) {
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
