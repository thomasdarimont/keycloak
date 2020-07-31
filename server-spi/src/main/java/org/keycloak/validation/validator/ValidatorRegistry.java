package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;

import java.util.List;

public interface ValidatorRegistry {

    List<Validator> getValidators(ValidationContext context);

    void register(Class<?> targetType, Validator validator);
}
