package org.keycloak.services.validation.validators;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.validator.Validator;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DefaultValidatorRegistry implements ValidatorRegistry {

    // validator on top of keys
    // user validators
    // client validators
    // realm validators
    // group validators
    // federation provider validators
    // identity provider validators
    // role validators
    // protocol mapper validators
    // ...
    private final ConcurrentMap<Class<?>, List<Validator>> validatorMap = new ConcurrentHashMap<>();

    @Override
    public List<Validator> getValidators(ValidationContext context) {

        List<Validator> validators = validatorMap.getOrDefault(context.getTargetType(), Collections.emptyList());
        return filterValidators(validators, context);
    }

    protected List<Validator> filterValidators(List<Validator> validators, ValidationContext context) {
        return validators.stream().filter(v -> v.isSupported(context) && v.isEnabled(context)).collect(Collectors.toList());
    }

    @Override
    public void register(Class<?> targetType, Validator validator) {
        validatorMap.computeIfAbsent(targetType, t -> new ArrayList<>()).add(validator);
    }
}
