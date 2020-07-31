package org.keycloak.validation.validator;

import java.util.Set;

public class ValidatorRegistration implements Comparable<ValidatorRegistration> {

    private final String key;

    private final Validator validator;

    private final Set<String> contextKeys;

    private final double order;

    public ValidatorRegistration(String key, Validator validator, double order, Set<String> contextKeys) {
        this.key = key;
        this.validator = validator;
        this.order = order;
        this.contextKeys = contextKeys;
    }

    public String getKey() {
        return key;
    }

    public Validator getValidator() {
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