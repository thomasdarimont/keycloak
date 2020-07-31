package org.keycloak.services.validation.validators;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.validator.Validator;
import org.keycloak.validation.validator.ValidatorRegistration;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Default {@link ValidatorRegistry} implementation.
 */
public class DefaultValidatorRegistry implements ValidatorRegistry {

    // TODO make validator lookup / storage more efficient

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
    private final ConcurrentMap<String, SortedSet<ValidatorRegistration>> validatorRegistrations = new ConcurrentHashMap<>();

    @Override
    public List<Validator<?>> getValidators(ValidationContext context, String key) {
        return filterValidators(validatorRegistrations.getOrDefault(key, Collections.emptySortedSet()), context);
    }

    protected List<Validator<?>> filterValidators(SortedSet<ValidatorRegistration> validators, ValidationContext context) {
        return validators.stream()
                .map(ValidatorRegistration::getValidator)
                .filter(v -> v.isSupported(context) && v.isEnabled(context))
                .collect(Collectors.toList());
    }

    @Override
    public void register(String key, Validator<?> validator, double order, String... contextKeys) {
        validatorRegistrations.computeIfAbsent(key, t -> new TreeSet<>())
                .add(new ValidatorRegistration(key, validator, order, new LinkedHashSet<>(Arrays.asList(contextKeys))));
    }
}
