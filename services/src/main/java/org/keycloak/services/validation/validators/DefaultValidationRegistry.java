package org.keycloak.services.validation.validators;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationRegistration;
import org.keycloak.validation.ValidationRegistry;

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
 * Default {@link ValidationRegistry} implementation.
 */
public class DefaultValidationRegistry implements ValidationRegistry {

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
    private final ConcurrentMap<String, SortedSet<ValidationRegistration>> validatorRegistrations = new ConcurrentHashMap<>();

    @Override
    public List<Validation<?>> getValidations(ValidationContext context, String key) {
        return filterValidators(validatorRegistrations.getOrDefault(key, Collections.emptySortedSet()), context);
    }

    protected List<Validation<?>> filterValidators(SortedSet<ValidationRegistration> validators, ValidationContext context) {
        return validators.stream()
                .map(ValidationRegistration::getValidation)
                .filter(v -> v.isSupported(context) && v.isEnabled(context))
                .collect(Collectors.toList());
    }

    @Override
    public void register(String key, Validation<?> validation, double order, String... contextKeys) {
        validatorRegistrations.computeIfAbsent(key, t -> new TreeSet<>())
                .add(new ValidationRegistration(key, validation, order, new LinkedHashSet<>(Arrays.asList(contextKeys))));
    }
}
