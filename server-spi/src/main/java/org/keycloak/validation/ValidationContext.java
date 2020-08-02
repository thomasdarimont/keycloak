package org.keycloak.validation;

import org.keycloak.models.RealmModel;

import java.util.Collections;
import java.util.Map;

/**
 * Denotes a context in which the Validation takes place. A {@link ValidationContext} should be created for a
 * batch of validation checks for a given entity.
 */
public class ValidationContext {

    // Note we use Strings here instead of enums to ease adding custom Keys
    public interface ValidationContextKey {

        String PROFILE_UPDATE = "user-profile-update";

        String REGISTRATION = "user-registration";

        // TODO define more validation contexts
    }

    /**
     * Denotes the ValidationTarget with predefined validation keys, e.g. Realm, User, Client, etc.
     *
     */
    // Note we use Strings here instead of enums to ease adding custom Keys
    public interface ValidationTarget {

        interface User extends ValidationTarget{

            String USERNAME = "user.username";

            String EMAIL = "user.email";

            String FIRSTNAME = "user.firstname";

            String LASTNAME = "user.lastname";

            // TODO define more validatable properties / attributes
        }

        // TODO define more validatable types with properties / attributes
    }

    private final RealmModel realm;

    // user registration, user profile update, client registration, realm creation
    private final String contextKey;

    // additional context specific attributes
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
