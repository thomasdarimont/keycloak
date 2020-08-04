package org.keycloak.validation;

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
    public boolean validate(ValidationKey key, Object value, NestedValidationContext context) {
        return this.delegate.validate(key, value, context);
    }

    public boolean isSupported(ValidationKey key, Object value, NestedValidationContext context) {
        return supported.test(key, value, context);
    }
}
