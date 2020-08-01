package org.keycloak.services.validation;

import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationRegistration;
import org.keycloak.validation.ValidationRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public Map<String, List<Validation<?>>> getValidations(ValidationContext context, Set<String> keys) {
        Map<String, List<Validation<?>>> validators = new LinkedHashMap<>();
        for (String key : keys) {
            SortedSet<ValidationRegistration> validatorRegistrationsForKey = validatorRegistrations.getOrDefault(key, Collections.emptySortedSet());
            List<Validation<?>> validatorsForKey = filterValidators(validatorRegistrationsForKey, context);
            validators.put(key, validatorsForKey);
        }
        return validators;
    }

    protected List<Validation<?>> filterValidators(SortedSet<ValidationRegistration> validators, ValidationContext context) {
        return validators.stream()
                .map(ValidationRegistration::getValidation)
                .filter(v -> v.isSupported(context) && v.isEnabled(context))
                .collect(Collectors.toList());
    }

    @Override
    public void register(String key, Validation<?> validation, double order, Set<String> contextKeys) {
        validatorRegistrations.computeIfAbsent(key, t -> new TreeSet<>())
                .add(new ValidationRegistration(key, validation, order, contextKeys));
    }
}
