package org.keycloak.validation;

import java.util.Set;

/**
 * Denotes a registration of a {@link Validation} with additional meta-data.
 */
public class ValidationRegistration implements Comparable<ValidationRegistration> {

    /**
     * Denotes the key of the attribute that the referenced {@link Validation} can validate.
     */
    private final String key;

    /**
     * The actual {@link Validation}
     */
    private final Validation validation;

    /**
     * Denotes the contexts in which the referenced validation can be applied.
     */
    private final Set<String> contextKeys;

    /**
     * The order in case multiple validation are registered for the same attribute.
     */
    private final double order;

    public ValidationRegistration(String key, Validation validation, double order, Set<String> contextKeys) {
        this.key = key;
        this.validation = validation;
        this.order = order;
        this.contextKeys = contextKeys;
    }

    public String getKey() {
        return key;
    }

    public Validation getValidation() {
        return validation;
    }

    public Set<String> getContextKeys() {
        return contextKeys;
    }

    public double getOrder() {
        return order;
    }

    @Override
    public int compareTo(ValidationRegistration that) {
        return Double.compare(this.getOrder(), that.getOrder());
    }
}
