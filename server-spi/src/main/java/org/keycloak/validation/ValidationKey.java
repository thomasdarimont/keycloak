package org.keycloak.validation;

/**
 * Denotes a validatable property, e.g. Realm Attributes, User Properties, Client Properties, etc.
 */
public interface ValidationKey {

    ValidationKey USER_USERNAME = UserProperty.USER_USERNAME;
    ValidationKey USER_EMAIL = UserProperty.USER_EMAIL;
    ValidationKey USER_FIRSTNAME = UserProperty.USER_FIRSTNAME;
    ValidationKey USER_LASTNAME = UserProperty.USER_LASTNAME;

    enum UserProperty implements ValidationKey {
        USER_USERNAME,
        USER_EMAIL,
        USER_FIRSTNAME,
        USER_LASTNAME,
    }

    // TODO define more validatable types with properties / attributes
}
