package org.keycloak.validation;

/**
 * Denotes a dedicated ValidationContext in which certain {@link Validation} rules should be applied, e.g. during User Registration,
 * User Profile change, Client Registration, Realm Definition, Identity Provider Configuration, etc.
 * {@link Validation Validation's} can be associated with different ValidationContextKey.
 * <p>
 * Users can create custom {@link ValidationContextKey ValidationContextKey's} by implementing this interface.
 * It is recommended that custom {@link ValidationContextKey} implementations are singletons, hence enums are a good choice.
 */
public interface ValidationContextKey {

    ValidationContextKey USER_PROFILE_UPDATE = DefaultContext.USER_PROFILE_UPDATE;

    ValidationContextKey USER_REGISTRATION = DefaultContext.USER_REGISTRATION;

    enum DefaultContext implements ValidationContextKey {

        USER_PROFILE_UPDATE,

        USER_REGISTRATION;
    }
}
