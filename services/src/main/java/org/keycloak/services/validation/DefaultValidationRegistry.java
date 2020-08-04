package org.keycloak.services.validation;

import org.keycloak.validation.NestedValidationContext;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationRegistration;
import org.keycloak.validation.ValidationRegistry;

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
    public Map<String, List<Validation>> getValidations(ValidationContext context, Set<String> keys, Object value) {
        Map<String, List<Validation>> validators = new LinkedHashMap<>();
        for (String key : keys) {
            SortedSet<ValidationRegistration> validatorRegistrationsForKey = validatorRegistrations.get(key);
            if (validatorRegistrationsForKey == null || validatorRegistrationsForKey.isEmpty()) {
                continue;
            }
            List<Validation> validatorsForKey = filterValidators(key, validatorRegistrationsForKey, context, value);
            validators.put(key, validatorsForKey);
        }
        return validators;
    }

    protected List<Validation> filterValidators(String key, SortedSet<ValidationRegistration> validators, ValidationContext context, Object value) {
        return validators.stream()
                .filter(vr -> vr.isEligibleForContextKey(context.getContextKey()))
                .map(ValidationRegistration::getValidation)
                .filter(v -> v.isSupported(key, value, context))
                .collect(Collectors.toList());
    }

    @Override
    public void register(String key, Validation validation, double order, Set<String> contextKeys) {
        validatorRegistrations.computeIfAbsent(key, t -> new TreeSet<>())
                .add(new ValidationRegistration(key, validation, order, contextKeys));
    }
}
