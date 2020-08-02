package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;

/**
 * {@link DelegatingValidation} allows to delegate to a given validation while customizing the checks for
 * enablement and context-specific support.
 */
public class DelegatingValidation implements Validation {

    private final Validation delegate;

    private final ValidationEnabled enabled;

    private final ValidationSupported supported;

    public DelegatingValidation(Validation delegate) {
        this(delegate, ValidationEnabled.ALWAYS, ValidationSupported.ALWAYS);
    }

    public DelegatingValidation(Validation delegate, ValidationEnabled enabled, ValidationSupported supported) {
        this.delegate = delegate;
        this.enabled = enabled;
        this.supported = supported;
    }

    @Override
    public boolean validate(String key, Object value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session) {
        return this.delegate.validate(key, value, context, problems, session);
    }

    public boolean isEnabled(String key, ValidationContext context) {
        return enabled.test(key, context);
    }

    public boolean isSupported(String key, Object value, ValidationContext context) {
        return supported.test(key, value, context);
    }
}
