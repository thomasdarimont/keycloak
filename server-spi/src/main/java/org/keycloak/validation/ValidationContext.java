package org.keycloak.validation;

import org.keycloak.models.RealmModel;

import java.util.Collections;
import java.util.Map;

public class ValidationContext {

    public static String USER_USERNAME_VALIDATION_KEY = "user.username";

    public static String USER_EMAIL_VALIDATION_KEY = "user.email";

    public static String USER_FIRSTNAME_VALIDATION_KEY = "user.firstname";

    public static String USER_LASTNAME_VALIDATION_KEY = "user.lastname";

    public interface ValidationContextKey {

        String PROFILE_UPDATE = "user-profile-update";

        String REGISTRATION = "user-registration";
    }

    public interface ValidationTarget {

        interface User {

            String USERNAME = "user.username";

            String EMAIL = "user.email";

            String FIRSTNAME = "user.firstname";

            String LASTNAME = "user.lastname";

        }
    }

    private final RealmModel realm;

    // user registration, user profile update, client registration, realm creation
    private final String contextKey;

    private final Map<String, Object> attributes;

    public ValidationContext(RealmModel realm, String contextKey) {
        this(realm, contextKey, Collections.emptyMap());
    }

    public ValidationContext(RealmModel realm, String contextKey, Map<String, Object> attributes) {
        this.realm = realm;
        this.contextKey = contextKey;
        this.attributes = attributes;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public String getContextKey() {
        return contextKey;
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
