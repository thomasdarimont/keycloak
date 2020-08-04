package org.keycloak.validation;

import org.keycloak.models.RealmModel;

import java.util.Collections;
import java.util.Map;

/**
 * Denotes a context in which the Validation takes place. A {@link ValidationContext} should be created for a
 * batch of validation checks for a given entity.
 */
public class ValidationContext {

    private final RealmModel realm;

    // user registration, user profile update, client registration, realm creation
    private final ValidationContextKey contextKey;

    // additional context specific attributes
    private final Map<String, Object> attributes;

    public ValidationContext(RealmModel realm, ValidationContextKey contextKey) {
        this(realm, contextKey, Collections.emptyMap());
    }

    public ValidationContext(RealmModel realm, ValidationContextKey contextKey, Map<String, Object> attributes) {
        this.realm = realm;
        this.contextKey = contextKey;
        this.attributes = attributes;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public ValidationContextKey getContextKey() {
        return contextKey;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public boolean getAttributeAsBoolean(String name) {
        return attributes.get(name) == Boolean.TRUE;
    }

    public String getAttributeAsString(String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
