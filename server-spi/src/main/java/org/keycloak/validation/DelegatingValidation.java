package org.keycloak.validation;

import org.keycloak.models.KeycloakSession;

import java.util.List;

/**
 * {@link DelegatingValidation} allows to delegate to a given validation while customizing the checks for
 * enablement and context-specific support.
 */
public class DelegatingValidation implements Validation {

    private final Validation delegate;

    private final ValidationSupported supported;

    public DelegatingValidation(Validation delegate) {
        this(delegate, ValidationSupported.ALWAYS);
    }

    public DelegatingValidation(Validation delegate, ValidationSupported supported) {
        this.delegate = delegate;
        this.supported = supported;
    }

    @Override
    public boolean validate(String key, Object value, ValidationContext context, List<ValidationProblem> problems, KeycloakSession session) {
        return this.delegate.validate(key, value, context, problems, session);
    }

    public boolean isSupported(String key, Object value, ValidationContext context) {
        return supported.test(key, value, context);
    }
}
