package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;

public abstract class AbstractValidator implements Validator, Comparable<AbstractValidator> {

    private final String key;

    private final double order;

    public AbstractValidator(String key, double order) {
        this.key = key;
        this.order = order;
    }

    @Override
    public String getKey() {
        return key;
    }

    public double getOrder() {
        return order;
    }

    public boolean isEnabled(ValidationContext validationContext) {
        return true;
    }

    public boolean isSupported(ValidationContext validationContext) {
        return true;
    }

    @Override
    public int compareTo(AbstractValidator that) {
        return Double.compare(this.order, that.getOrder());
    }
}
