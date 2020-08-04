package org.keycloak.validation;

import java.util.Set;

/**
 * Denotes a registration of a {@link Validation} with additional meta-data.
 */
public class ValidationRegistration implements Comparable<ValidationRegistration> {

    /**
     * Denotes the key of the attribute that the referenced {@link Validation} can validate.
     */
    private final ValidationKey key;

    /**
     * The actual {@link Validation}
     */
    private final Validation validation;

    /**
     * Denotes the contexts in which the referenced validation can be applied.
     *
     * @see ValidationContextKey
     */
    private final Set<ValidationContextKey> contextKeys;

    /**
     * The order in case multiple validation are registered for the same attribute.
     */
    private final double order;

    public ValidationRegistration(ValidationKey key, Validation validation, double order, Set<ValidationContextKey> contextKeys) {
        this.key = key;
        this.validation = validation;
        this.order = order;
        this.contextKeys = contextKeys;
    }

    public ValidationKey getKey() {
        return key;
    }

    public Validation getValidation() {
        return validation;
    }

    public Set<ValidationContextKey> getContextKeys() {
        return contextKeys;
    }

    public double getOrder() {
        return order;
    }

    @Override
    public int compareTo(ValidationRegistration that) {
        return Double.compare(this.getOrder(), that.getOrder());
    }

    public boolean isEligibleForContextKey(ValidationContextKey contextKey) {
        return getContextKeys().contains(contextKey);
    }
}
