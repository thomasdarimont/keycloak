package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class DelegatingValidation implements Validation {

    private final Validation delegate;

    private final Predicate<ValidationContext> enabled;

    private final BiPredicate<ValidationContext, Object> supported;

    public DelegatingValidation(Validation delegate) {
        this(delegate, c -> true, (c, v) -> true);
    }

    public DelegatingValidation(Validation delegate, Predicate<ValidationContext> enabled, BiPredicate<ValidationContext, Object> supported) {
        this.delegate = delegate;
        this.enabled = enabled;
        this.supported = supported;
    }

    @Override
    public boolean validate(String key, Object value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session) {
        return this.delegate.validate(key, value, context, problems, session);
    }

    public boolean isEnabled(ValidationContext context) {
        return enabled.test(context);
    }

    public boolean isSupported(ValidationContext context, Object value) {
        return supported.test(context, value);
    }
}
