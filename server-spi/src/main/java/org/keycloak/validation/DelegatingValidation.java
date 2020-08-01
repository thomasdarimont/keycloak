package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;
import java.util.function.Predicate;

public class DelegatingValidation<V> implements Validation<V> {

    private final Validation<V> delegate;

    private final Predicate<ValidationContext> enabled;

    private final Predicate<ValidationContext> supported;

    public DelegatingValidation(Validation<V> delegate) {
        this(delegate, c -> true, c -> true);
    }

    public DelegatingValidation(Validation<V> delegate, Predicate<ValidationContext> enabled, Predicate<ValidationContext> supported) {
        this.delegate = delegate;
        this.enabled = enabled;
        this.supported = supported;
    }

    @Override
    public boolean validate(String key, V value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session) {
        return this.delegate.validate(key, value, context, problems, session);
    }

    public boolean isEnabled(ValidationContext context) {
        return enabled.test(context);
    }

    public boolean isSupported(ValidationContext context) {
        return supported.test(context);
    }
}
