package org.keycloak.services.validation;

import org.jboss.logging.Logger;
import org.keycloak.validation.Validation;
import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationContextKey;
import org.keycloak.validation.ValidationKey;
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
import java.util.stream.Stream;

/**
 * Default {@link ValidationRegistry} implementation.
 */
public class DefaultValidationRegistry implements ValidationRegistry {

    private static final Logger LOGGER = Logger.getLogger(DefaultValidationProvider.class);

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
    private final ConcurrentMap<ValidationKey, SortedSet<ValidationRegistration>> validatorRegistrations = new ConcurrentHashMap<>();

    protected Stream<ValidationRegistration> getValidationRegistrationsStream(ValidationKey key) {

        SortedSet<ValidationRegistration> registrations = validatorRegistrations.get(key);

        if (registrations == null || registrations.isEmpty()) {
            return Stream.empty();
        }

        return registrations.stream();
    }

    @Override
    public List<Validation> getValidations(ValidationKey key) {
        return getValidationRegistrationsStream(key)
                .map(ValidationRegistration::getValidation)
                .collect(Collectors.toList());
    }

    @Override
    public Map<ValidationKey, List<Validation>> getValidations(Set<ValidationKey> keys) {

        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ValidationKey, List<Validation>> validationMap = new LinkedHashMap<>();
        for (ValidationKey key : keys) {
            List<Validation> validations = getValidations(key);
            validationMap.put(key, validations);
        }

        return validationMap;
    }

    @Override
    public Map<ValidationKey, List<Validation>> resolveValidations(ValidationContext context, Set<ValidationKey> keys, Object value) {

        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ValidationKey, List<Validation>> validationMap = new LinkedHashMap<>();
        for (ValidationKey key : keys) {
            List<Validation> validations = resolveValidations(context, key, value);
            validationMap.put(key, validations);
        }

        return validationMap;
    }

    @Override
    public List<Validation> resolveValidations(ValidationContext context, ValidationKey key, Object value) {
        return filterValidators(key, getValidationRegistrationsStream(key), context, value);
    }

    protected List<Validation> filterValidators(ValidationKey key, Stream<ValidationRegistration> registrations, ValidationContext context, Object value) {

        return registrations
                .filter(vr -> vr.isEligibleForContextKey(context.getContextKey()))
                .map(ValidationRegistration::getValidation)
                .filter(v -> v.isSupported(key, value, context))
                .collect(Collectors.toList());
    }

    @Override
    public void registerValidation(Validation validation, ValidationKey key, double order, Set<ValidationContextKey> contextKeys) {

        ValidationRegistration registration = new ValidationRegistration(key, validation, order, contextKeys);

        boolean wasNew = validatorRegistrations.computeIfAbsent(key, t -> new TreeSet<>()).add(registration);
        if (!wasNew) {
            LOGGER.debugf("Validation %s for key %s replaced existing validation.", validation.getClass().getName(), key);
        }
    }
}
