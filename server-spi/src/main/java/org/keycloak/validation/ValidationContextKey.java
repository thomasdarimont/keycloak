package org.keycloak.validation;

// Note we use an interface here instead of the enum directly  to ease adding custom ValidationContextKey's
public interface ValidationContextKey {

    ValidationContextKey USER_PROFILE_UPDATE = DefaultContext.USER_PROFILE_UPDATE;

    ValidationContextKey USER_REGISTRATION = DefaultContext.USER_REGISTRATION;

    enum DefaultContext implements ValidationContextKey {

        USER_PROFILE_UPDATE,

        USER_REGISTRATION;
    }
}
