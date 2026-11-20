/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.events;

/**
 * The type of a user {@link Event}.
 * <p>
 * The event types that ship with Keycloak are defined in {@link BuiltinEventType} and are re-exported here as
 * constants, so that {@code EventType.LOGIN} keeps working. Extensions can define additional event types by
 * implementing this interface, usually with an enum, and registering them through an {@link EventTypeProviderFactory}.
 * <p>
 * Event types are identified by their {@link #name()}, which must be unique across all registered event types.
 * The type that is emitted for a failed event is, by convention, the type whose name is the name of the success
 * type followed by {@code _ERROR}.
 * <p>
 * Implementation note: this interface must not declare default methods. Initializing {@link BuiltinEventType} would
 * then also initialize this interface, whose constants would observe the not yet initialized enum constants as
 * {@code null}.
 *
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
@SuppressWarnings("deprecation")
public interface EventType {

    EventType LOGIN = BuiltinEventType.LOGIN;
    EventType LOGIN_ERROR = BuiltinEventType.LOGIN_ERROR;
    EventType REGISTER = BuiltinEventType.REGISTER;
    EventType REGISTER_ERROR = BuiltinEventType.REGISTER_ERROR;
    EventType LOGOUT = BuiltinEventType.LOGOUT;
    EventType LOGOUT_ERROR = BuiltinEventType.LOGOUT_ERROR;

    EventType CODE_TO_TOKEN = BuiltinEventType.CODE_TO_TOKEN;
    EventType CODE_TO_TOKEN_ERROR = BuiltinEventType.CODE_TO_TOKEN_ERROR;

    EventType CLIENT_LOGIN = BuiltinEventType.CLIENT_LOGIN;
    EventType CLIENT_LOGIN_ERROR = BuiltinEventType.CLIENT_LOGIN_ERROR;

    EventType REFRESH_TOKEN = BuiltinEventType.REFRESH_TOKEN;
    EventType REFRESH_TOKEN_ERROR = BuiltinEventType.REFRESH_TOKEN_ERROR;

    @Deprecated
    EventType VALIDATE_ACCESS_TOKEN = BuiltinEventType.VALIDATE_ACCESS_TOKEN;
    @Deprecated
    EventType VALIDATE_ACCESS_TOKEN_ERROR = BuiltinEventType.VALIDATE_ACCESS_TOKEN_ERROR;
    EventType INTROSPECT_TOKEN = BuiltinEventType.INTROSPECT_TOKEN;
    EventType INTROSPECT_TOKEN_ERROR = BuiltinEventType.INTROSPECT_TOKEN_ERROR;

    EventType FEDERATED_IDENTITY_LINK = BuiltinEventType.FEDERATED_IDENTITY_LINK;
    EventType FEDERATED_IDENTITY_LINK_ERROR = BuiltinEventType.FEDERATED_IDENTITY_LINK_ERROR;
    EventType REMOVE_FEDERATED_IDENTITY = BuiltinEventType.REMOVE_FEDERATED_IDENTITY;
    EventType REMOVE_FEDERATED_IDENTITY_ERROR = BuiltinEventType.REMOVE_FEDERATED_IDENTITY_ERROR;


    EventType UPDATE_EMAIL = BuiltinEventType.UPDATE_EMAIL;
    EventType UPDATE_EMAIL_ERROR = BuiltinEventType.UPDATE_EMAIL_ERROR;
    EventType UPDATE_PROFILE = BuiltinEventType.UPDATE_PROFILE;
    EventType UPDATE_PROFILE_ERROR = BuiltinEventType.UPDATE_PROFILE_ERROR;

    @Deprecated
    EventType UPDATE_PASSWORD = BuiltinEventType.UPDATE_PASSWORD;
    @Deprecated
    EventType UPDATE_PASSWORD_ERROR = BuiltinEventType.UPDATE_PASSWORD_ERROR;

    @Deprecated
    EventType UPDATE_TOTP = BuiltinEventType.UPDATE_TOTP;
    @Deprecated
    EventType UPDATE_TOTP_ERROR = BuiltinEventType.UPDATE_TOTP_ERROR;
    EventType VERIFY_EMAIL = BuiltinEventType.VERIFY_EMAIL;
    EventType VERIFY_EMAIL_ERROR = BuiltinEventType.VERIFY_EMAIL_ERROR;
    EventType VERIFY_PROFILE = BuiltinEventType.VERIFY_PROFILE;
    EventType VERIFY_PROFILE_ERROR = BuiltinEventType.VERIFY_PROFILE_ERROR;

    @Deprecated
    EventType REMOVE_TOTP = BuiltinEventType.REMOVE_TOTP;
    @Deprecated
    EventType REMOVE_TOTP_ERROR = BuiltinEventType.REMOVE_TOTP_ERROR;

    EventType GRANT_CONSENT = BuiltinEventType.GRANT_CONSENT;
    EventType GRANT_CONSENT_ERROR = BuiltinEventType.GRANT_CONSENT_ERROR;
    EventType UPDATE_CONSENT = BuiltinEventType.UPDATE_CONSENT;
    EventType UPDATE_CONSENT_ERROR = BuiltinEventType.UPDATE_CONSENT_ERROR;
    EventType REVOKE_GRANT = BuiltinEventType.REVOKE_GRANT;
    EventType REVOKE_GRANT_ERROR = BuiltinEventType.REVOKE_GRANT_ERROR;

    EventType SEND_VERIFY_EMAIL = BuiltinEventType.SEND_VERIFY_EMAIL;
    EventType SEND_VERIFY_EMAIL_ERROR = BuiltinEventType.SEND_VERIFY_EMAIL_ERROR;
    EventType SEND_RESET_PASSWORD = BuiltinEventType.SEND_RESET_PASSWORD;
    EventType SEND_RESET_PASSWORD_ERROR = BuiltinEventType.SEND_RESET_PASSWORD_ERROR;
    EventType SEND_IDENTITY_PROVIDER_LINK = BuiltinEventType.SEND_IDENTITY_PROVIDER_LINK;
    EventType SEND_IDENTITY_PROVIDER_LINK_ERROR = BuiltinEventType.SEND_IDENTITY_PROVIDER_LINK_ERROR;
    EventType RESET_PASSWORD = BuiltinEventType.RESET_PASSWORD;
    EventType RESET_PASSWORD_ERROR = BuiltinEventType.RESET_PASSWORD_ERROR;

    EventType RESTART_AUTHENTICATION = BuiltinEventType.RESTART_AUTHENTICATION;
    EventType RESTART_AUTHENTICATION_ERROR = BuiltinEventType.RESTART_AUTHENTICATION_ERROR;

    EventType INVALID_SIGNATURE = BuiltinEventType.INVALID_SIGNATURE;
    EventType INVALID_SIGNATURE_ERROR = BuiltinEventType.INVALID_SIGNATURE_ERROR;
    EventType REGISTER_NODE = BuiltinEventType.REGISTER_NODE;
    EventType REGISTER_NODE_ERROR = BuiltinEventType.REGISTER_NODE_ERROR;
    EventType UNREGISTER_NODE = BuiltinEventType.UNREGISTER_NODE;
    EventType UNREGISTER_NODE_ERROR = BuiltinEventType.UNREGISTER_NODE_ERROR;

    EventType USER_INFO_REQUEST = BuiltinEventType.USER_INFO_REQUEST;
    EventType USER_INFO_REQUEST_ERROR = BuiltinEventType.USER_INFO_REQUEST_ERROR;

    EventType IDENTITY_PROVIDER_LINK_ACCOUNT = BuiltinEventType.IDENTITY_PROVIDER_LINK_ACCOUNT;
    EventType IDENTITY_PROVIDER_LINK_ACCOUNT_ERROR = BuiltinEventType.IDENTITY_PROVIDER_LINK_ACCOUNT_ERROR;
    EventType IDENTITY_PROVIDER_LOGIN = BuiltinEventType.IDENTITY_PROVIDER_LOGIN;
    EventType IDENTITY_PROVIDER_LOGIN_ERROR = BuiltinEventType.IDENTITY_PROVIDER_LOGIN_ERROR;
    EventType IDENTITY_PROVIDER_FIRST_LOGIN = BuiltinEventType.IDENTITY_PROVIDER_FIRST_LOGIN;
    EventType IDENTITY_PROVIDER_FIRST_LOGIN_ERROR = BuiltinEventType.IDENTITY_PROVIDER_FIRST_LOGIN_ERROR;
    EventType IDENTITY_PROVIDER_POST_LOGIN = BuiltinEventType.IDENTITY_PROVIDER_POST_LOGIN;
    EventType IDENTITY_PROVIDER_POST_LOGIN_ERROR = BuiltinEventType.IDENTITY_PROVIDER_POST_LOGIN_ERROR;
    EventType IDENTITY_PROVIDER_RESPONSE = BuiltinEventType.IDENTITY_PROVIDER_RESPONSE;
    EventType IDENTITY_PROVIDER_RESPONSE_ERROR = BuiltinEventType.IDENTITY_PROVIDER_RESPONSE_ERROR;
    EventType IDENTITY_PROVIDER_RETRIEVE_TOKEN = BuiltinEventType.IDENTITY_PROVIDER_RETRIEVE_TOKEN;
    EventType IDENTITY_PROVIDER_RETRIEVE_TOKEN_ERROR = BuiltinEventType.IDENTITY_PROVIDER_RETRIEVE_TOKEN_ERROR;
    EventType IMPERSONATE = BuiltinEventType.IMPERSONATE;
    EventType IMPERSONATE_ERROR = BuiltinEventType.IMPERSONATE_ERROR;
    EventType CUSTOM_REQUIRED_ACTION = BuiltinEventType.CUSTOM_REQUIRED_ACTION;
    EventType CUSTOM_REQUIRED_ACTION_ERROR = BuiltinEventType.CUSTOM_REQUIRED_ACTION_ERROR;
    EventType EXECUTE_ACTIONS = BuiltinEventType.EXECUTE_ACTIONS;
    EventType EXECUTE_ACTIONS_ERROR = BuiltinEventType.EXECUTE_ACTIONS_ERROR;

    EventType EXECUTE_ACTION_TOKEN = BuiltinEventType.EXECUTE_ACTION_TOKEN;
    EventType EXECUTE_ACTION_TOKEN_ERROR = BuiltinEventType.EXECUTE_ACTION_TOKEN_ERROR;

    EventType CLIENT_INFO = BuiltinEventType.CLIENT_INFO;
    EventType CLIENT_INFO_ERROR = BuiltinEventType.CLIENT_INFO_ERROR;
    EventType CLIENT_REGISTER = BuiltinEventType.CLIENT_REGISTER;
    EventType CLIENT_REGISTER_ERROR = BuiltinEventType.CLIENT_REGISTER_ERROR;
    EventType CLIENT_UPDATE = BuiltinEventType.CLIENT_UPDATE;
    EventType CLIENT_UPDATE_ERROR = BuiltinEventType.CLIENT_UPDATE_ERROR;
    EventType CLIENT_DELETE = BuiltinEventType.CLIENT_DELETE;
    EventType CLIENT_DELETE_ERROR = BuiltinEventType.CLIENT_DELETE_ERROR;

    EventType CLIENT_INITIATED_ACCOUNT_LINKING = BuiltinEventType.CLIENT_INITIATED_ACCOUNT_LINKING;
    EventType CLIENT_INITIATED_ACCOUNT_LINKING_ERROR = BuiltinEventType.CLIENT_INITIATED_ACCOUNT_LINKING_ERROR;
    EventType TOKEN_EXCHANGE = BuiltinEventType.TOKEN_EXCHANGE;
    EventType TOKEN_EXCHANGE_ERROR = BuiltinEventType.TOKEN_EXCHANGE_ERROR;

    EventType OAUTH2_DEVICE_AUTH = BuiltinEventType.OAUTH2_DEVICE_AUTH;
    EventType OAUTH2_DEVICE_AUTH_ERROR = BuiltinEventType.OAUTH2_DEVICE_AUTH_ERROR;
    EventType OAUTH2_DEVICE_VERIFY_USER_CODE = BuiltinEventType.OAUTH2_DEVICE_VERIFY_USER_CODE;
    EventType OAUTH2_DEVICE_VERIFY_USER_CODE_ERROR = BuiltinEventType.OAUTH2_DEVICE_VERIFY_USER_CODE_ERROR;
    EventType OAUTH2_DEVICE_CODE_TO_TOKEN = BuiltinEventType.OAUTH2_DEVICE_CODE_TO_TOKEN;
    EventType OAUTH2_DEVICE_CODE_TO_TOKEN_ERROR = BuiltinEventType.OAUTH2_DEVICE_CODE_TO_TOKEN_ERROR;

    EventType AUTHREQID_TO_TOKEN = BuiltinEventType.AUTHREQID_TO_TOKEN;
    EventType AUTHREQID_TO_TOKEN_ERROR = BuiltinEventType.AUTHREQID_TO_TOKEN_ERROR;

    EventType PERMISSION_TOKEN = BuiltinEventType.PERMISSION_TOKEN;
    EventType PERMISSION_TOKEN_ERROR = BuiltinEventType.PERMISSION_TOKEN_ERROR;

    EventType DELETE_ACCOUNT = BuiltinEventType.DELETE_ACCOUNT;
    EventType DELETE_ACCOUNT_ERROR = BuiltinEventType.DELETE_ACCOUNT_ERROR;

    // PAR request.
    EventType PUSHED_AUTHORIZATION_REQUEST = BuiltinEventType.PUSHED_AUTHORIZATION_REQUEST;
    EventType PUSHED_AUTHORIZATION_REQUEST_ERROR = BuiltinEventType.PUSHED_AUTHORIZATION_REQUEST_ERROR;

    EventType USER_DISABLED_BY_PERMANENT_LOCKOUT = BuiltinEventType.USER_DISABLED_BY_PERMANENT_LOCKOUT;
    EventType USER_DISABLED_BY_PERMANENT_LOCKOUT_ERROR = BuiltinEventType.USER_DISABLED_BY_PERMANENT_LOCKOUT_ERROR;

    EventType USER_DISABLED_BY_TEMPORARY_LOCKOUT = BuiltinEventType.USER_DISABLED_BY_TEMPORARY_LOCKOUT;
    EventType USER_DISABLED_BY_TEMPORARY_LOCKOUT_ERROR = BuiltinEventType.USER_DISABLED_BY_TEMPORARY_LOCKOUT_ERROR;

    EventType OAUTH2_EXTENSION_GRANT = BuiltinEventType.OAUTH2_EXTENSION_GRANT;
    EventType OAUTH2_EXTENSION_GRANT_ERROR = BuiltinEventType.OAUTH2_EXTENSION_GRANT_ERROR;

    EventType FEDERATED_IDENTITY_OVERRIDE_LINK = BuiltinEventType.FEDERATED_IDENTITY_OVERRIDE_LINK;
    EventType FEDERATED_IDENTITY_OVERRIDE_LINK_ERROR = BuiltinEventType.FEDERATED_IDENTITY_OVERRIDE_LINK_ERROR;

    EventType UPDATE_CREDENTIAL = BuiltinEventType.UPDATE_CREDENTIAL;
    EventType UPDATE_CREDENTIAL_ERROR = BuiltinEventType.UPDATE_CREDENTIAL_ERROR;

    EventType REMOVE_CREDENTIAL = BuiltinEventType.REMOVE_CREDENTIAL;
    EventType REMOVE_CREDENTIAL_ERROR = BuiltinEventType.REMOVE_CREDENTIAL_ERROR;

    EventType INVITE_ORG = BuiltinEventType.INVITE_ORG;
    EventType INVITE_ORG_ERROR = BuiltinEventType.INVITE_ORG_ERROR;

    EventType USER_SESSION_DELETED = BuiltinEventType.USER_SESSION_DELETED;
    EventType USER_SESSION_DELETED_ERROR = BuiltinEventType.USER_SESSION_DELETED_ERROR;

    EventType VERIFIABLE_CREDENTIAL_REQUEST = BuiltinEventType.VERIFIABLE_CREDENTIAL_REQUEST;
    EventType VERIFIABLE_CREDENTIAL_REQUEST_ERROR = BuiltinEventType.VERIFIABLE_CREDENTIAL_REQUEST_ERROR;
    EventType VERIFIABLE_CREDENTIAL_OFFER_REQUEST = BuiltinEventType.VERIFIABLE_CREDENTIAL_OFFER_REQUEST;
    EventType VERIFIABLE_CREDENTIAL_OFFER_REQUEST_ERROR = BuiltinEventType.VERIFIABLE_CREDENTIAL_OFFER_REQUEST_ERROR;
    EventType VERIFIABLE_CREDENTIAL_NONCE_REQUEST = BuiltinEventType.VERIFIABLE_CREDENTIAL_NONCE_REQUEST;
    EventType VERIFIABLE_CREDENTIAL_NONCE_REQUEST_ERROR = BuiltinEventType.VERIFIABLE_CREDENTIAL_NONCE_REQUEST_ERROR;
    EventType VERIFIABLE_CREDENTIAL_CREATE_OFFER = BuiltinEventType.VERIFIABLE_CREDENTIAL_CREATE_OFFER;
    EventType VERIFIABLE_CREDENTIAL_CREATE_OFFER_ERROR = BuiltinEventType.VERIFIABLE_CREDENTIAL_CREATE_OFFER_ERROR;
    EventType VERIFIABLE_CREDENTIAL_PRE_AUTHORIZED_GRANT = BuiltinEventType.VERIFIABLE_CREDENTIAL_PRE_AUTHORIZED_GRANT;
    EventType VERIFIABLE_CREDENTIAL_PRE_AUTHORIZED_GRANT_ERROR = BuiltinEventType.VERIFIABLE_CREDENTIAL_PRE_AUTHORIZED_GRANT_ERROR;

    EventType JWT_AUTHORIZATION_GRANT = BuiltinEventType.JWT_AUTHORIZATION_GRANT;
    EventType JWT_AUTHORIZATION_GRANT_ERROR = BuiltinEventType.JWT_AUTHORIZATION_GRANT_ERROR;

    /**
     * The unique name of this event type, as it is stored, shown in the admin console and used in configuration.
     */
    String name();

    /**
     * Determines whether this event is stored when the admin has not set a specific set of event types to save.
     */
    boolean isSaveByDefault();

    /**
     * Returns the event type with the given name, considering the built-in event types as well as the custom event
     * types. Custom event types are only known once the Keycloak session factory has loaded the provider factories,
     * so they cannot be resolved from {@link org.keycloak.provider.ProviderFactory#init}.
     *
     * @throws IllegalArgumentException if there is no event type with the given name
     */
    static EventType valueOf(String name) {
        return EventTypeRegistry.valueOf(name);
    }

    /**
     * Returns all known event types: the built-in event types followed by the custom event types, see
     * {@link #valueOf(String)} for when those are available.
     */
    static EventType[] values() {
        return EventTypeRegistry.values();
    }
}
