package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;

import java.util.List;

public interface ValidatorRegistry {

    List<Validator> getValidators(ValidationContext context, String key);

    void register(String key, Validator validator, double order, String... contextKeys);
}
