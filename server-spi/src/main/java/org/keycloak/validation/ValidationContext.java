package org.keycloak.validation;

import org.keycloak.models.RealmModel;

import java.util.Collections;
import java.util.Map;

public class ValidationContext {

    private final RealmModel realm;

    // user registration, user profile update, client registration, realm creation
    private final String contextKey;

    private final Class<?> targetType;

    private final Map<String, Object> attributes;

    public ValidationContext(RealmModel realm, String contextKey, Class<?> targetType) {
        this(realm, contextKey, targetType, Collections.emptyMap());
    }

    public ValidationContext(RealmModel realm, String contextKey, Class<?> targetType, Map<String, Object> attributes) {
        this.realm = realm;
        this.contextKey = contextKey;
        this.targetType = targetType;
        this.attributes = attributes;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public String getContextKey() {
        return contextKey;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean getAttributeBoolean(String name) {
        return attributes.get(name) == Boolean.TRUE;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public String getAttributeString(String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
