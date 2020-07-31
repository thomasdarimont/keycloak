package org.keycloak.validation.validator;

import java.util.Set;

/**
 * Denotes a registration of a {@link Validator} with additional meta-data.
 */
public class ValidatorRegistration implements Comparable<ValidatorRegistration> {

    /**
     * Denotes the key of the attribute that the referenced {@link Validator} can validate.
     */
    private final String key;

    /**
     * The actual {@link Validator}
     */
    private final Validator<?> validator;

    /**
     * Denotes the contexts in which the referenced validator can be applied.
     */
    private final Set<String> contextKeys;

    /**
     * The order in case multiple validators are registered for the same attribute.
     */
    private final double order;

    public ValidatorRegistration(String key, Validator<?> validator, double order, Set<String> contextKeys) {
        this.key = key;
        this.validator = validator;
        this.order = order;
        this.contextKeys = contextKeys;
    }

    public String getKey() {
        return key;
    }

    public Validator<?> getValidator() {
        return validator;
    }

    public Set<String> getContextKeys() {
        return contextKeys;
    }

    public double getOrder() {
        return order;
    }

    @Override
    public int compareTo(ValidatorRegistration that) {
        return Double.compare(this.getOrder(), that.getOrder());
    }
}