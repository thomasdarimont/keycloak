package org.keycloak.validation.validator;

import java.util.function.Function;

public class DelegatingValidator extends AbstractDelegatingValidator {

    public DelegatingValidator(Class<?> targetType, String key, Validator delegate, double order) {
        super(targetType, key, delegate, order);
    }

    public static <V> DelegatingValidator forNamedProperty(String key, Class<?> targetType, Function<Object, V> extractor, ValueValidation<V> check, double order) {
        return new DelegatingValidator(targetType, key, createValidatorAdapter(extractor, check), order);
    }
}
