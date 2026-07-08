/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.protocol.oidc.rar;

import java.util.List;
import java.util.Set;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.Provider;
import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;

/**
 * Provider interface for processing authorization_details parameter in OAuth2/OIDC authorization and token requests.
 * This follows the RAR (Rich Authorization Requests) specification and allows different
 * implementations to handle various types of authorization details.
 * The authorization_details parameter can be used in both authorization requests and token requests
 * (as specified for example in the OpenID for Verifiable Credential Issuance specification).
 *
 * @author <a href="mailto:Forkim.Akwichek@adorsys.com">Forkim Akwichek</a>
 */
public interface AuthorizationDetailsProcessor<ADR extends AuthorizationDetailsJSONRepresentation> extends Provider {

    /**
     * Checks if this processor should be regarded as supported in the running context.
     */
    boolean isSupported();

    /**
     * Checks if this processor is able to process the given type of authorization_details.
     * @param type
     * @return
     */
    default boolean isSupportedType(String type) {
        return getSupportedTypes().contains(type);
    }

    /**
     * @return supported type of authorization_details "type" claim, which this processor is able to process. This should usually correspond with the "providerId" of
     * the {@link AuthorizationDetailsProcessorFactory}, which created this processor
     *
     * @deprecated use {@link #getSupportedTypes()} instead
     */
    @Deprecated
    default String getSupportedType() {
        return getSupportedTypes().iterator().next();
    }

    /**
     * @return supported types of authorization_details "type" claim, which this processor is able to process.
     */
    Set<String> getSupportedTypes();

    /**
     * @return supported Java type of {@link AuthorizationDetailsJSONRepresentation} subclass, which this processor can create in the token response
     * @deprecated use {@link #getSupportedResponseJavaType(String)} instead
     */
    @Deprecated
    default Class<ADR> getSupportedResponseJavaType() {
        return getSupportedResponseJavaType(getSupportedTypes().iterator().next());
    }

    /**
     * @return supported Java type of {@link AuthorizationDetailsJSONRepresentation} subclass for the given type, which this processor can create in the token response
     *
     * @param type the type of the authorization_details
     */
    Class<ADR> getSupportedResponseJavaType(String type);

    /**
     * Validates an authorization detail against supported credentials and other constraints.
     */
    ADR validateAuthorizationDetail(AuthorizationDetailsJSONRepresentation authzDetail) throws InvalidAuthorizationDetailsException;

    /**
     * Processes the authorization_details parameter and returns a response if this processor
     * is able to handle the given authorization_details parameter.
     *
     * @param userSession                   the user session
     * @param clientSessionCtx              the client session context
     * @param authorizationDetailsMember the authorization_details member (usually one member from the list) sent in the "authorization_details" request parameter
     * @return authorization details response if this processor can handle the parameter, null if the parameter is incompatible with this processor
     */
    ADR process(UserSessionModel userSession,
                ClientSessionContext clientSessionCtx,
                AuthorizationDetailsJSONRepresentation authorizationDetailsMember) throws InvalidAuthorizationDetailsException;

    /**
     * Method is invoked in cases when authorization_details parameter is missing in the request. It allows processor to
     * generate authorization details response in such a case
     *
     * @param userSession      the user session
     * @param clientSessionCtx the client session context
     * @return authorization details response if this processor can handle current request in case that authorization_details parameter was not provided
     */
    List<ADR> handleMissingAuthorizationDetails(UserSessionModel userSession,
                                                ClientSessionContext clientSessionCtx) throws InvalidAuthorizationDetailsException;

    /**
     * Method is invoked when authorization_details was used in the authorization request but is missing from the token request.
     * This method should process the stored authorization_details and ensure they are returned in the token response.
     *
     * @param userSession       the user session
     * @param clientSessionCtx  the client session context
     * @param storedAuthDetailsMember the parsed member (usually one member of the list) from the authorization_details parameter that were stored during the authorization request
     * @return authorization details response if this processor can handle the stored authorization_details, null if the processor cannot handle the stored authorization_details
     */
    ADR processStoredAuthorizationDetails(UserSessionModel userSession,
                                          ClientSessionContext clientSessionCtx,
                                          AuthorizationDetailsJSONRepresentation storedAuthDetailsMember) throws InvalidAuthorizationDetailsException;

    /**
     * Hook method called after authorization_details are processed and before the token response is created.
     * This allows authorization details processors to perform post-processing actions (e.g., creating state objects).
     *
     * @param userSession      the user session
     * @param clientSessionCtx the client session context
     * @param authorizationDetailsResponse The response object of the proper type, which is supposed to be processed by this processor.
     */
    void afterAuthorizationDetailsProcessed(UserSessionModel userSession,
                                            ClientSessionContext clientSessionCtx,
                                            ADR authorizationDetailsResponse);


    /**
     * Sanitize authorization details before they are sent as part of the Token Response
     * https://github.com/keycloak/keycloak/issues/50079
     *
     * @param authzDetail The typed authorization detail
     * @return A sanitized clone of the authorization detail
     */
    default ADR sanitizeBeforeSendingTokenResponse(ADR authzDetail) {
        return authzDetail;
    }

    /**
     * @param authzDetailsResponse all the authorizationDetails. May contain also authorizationDetails entries, with different "type" than the type understandable by this processor
     * @return sublist of the list provided by "authDetailsResponse" parameter, which will contain just the authorizationDetails of the corresponding type of this processor.
     */
    default List<ADR> getSupportedAuthorizationDetails(List<AuthorizationDetailsJSONRepresentation> authzDetailsResponse) {
        if (authzDetailsResponse == null) {
            return null;
        }
        return authzDetailsResponse.stream()
                .filter(authDetailsResponse -> this.isSupportedType(authDetailsResponse.getType()))
                .map(this::narrowRepresentation)
                .toList();
    }

    ADR narrowRepresentation(AuthorizationDetailsJSONRepresentation authzDetail);

    /**
     * Converts the given {@code authorization_details} entry into a read-only, theme-facing structure so that it can be
     * rendered on the OAuth grant (consent) screen. Whether it is actually shown is gated elsewhere by associating the
     * type with a requested client scope. The default produces a generic tree from the RFC 9396 common fields and any
     * type-specific data (see {@link AuthorizationDetailDisplay#generic}); processors override this to curate the
     * output, and can branch on {@link AuthorizationDetailsJSONRepresentation#getType()} when they support several types.
     * Return {@code null} to suppress rendering for a given entry.
     *
     * @param authzDetail a single {@code authorization_details} entry whose type is supported by this processor
     * @return a display representation, or {@code null} if this entry should not be rendered on the consent screen
     */
    default AuthorizationDetailDisplay toConsentDisplay(AuthorizationDetailsJSONRepresentation authzDetail) {
        return AuthorizationDetailDisplay.generic(authzDetail);
    }
}
