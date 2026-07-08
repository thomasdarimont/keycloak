package org.keycloak.tests.oid4vc;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.keycloak.OID4VCConstants;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.protocol.oid4vc.model.OID4VCAuthorizationDetail;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.util.JsonSerialization;

import static org.keycloak.OAuth2Constants.AUTHORIZATION_DETAILS;

/**
 * Utility access to OID4VCAuthorizationDetails
 */
public final class OID4VCAuthorizationDetailsUtil {

    // Hide ctor
    private OID4VCAuthorizationDetailsUtil() {}

    /**
     * Access the OID4VCI-specific authorization details from the token endpoint response.
     * Entries whose type is not {@link OID4VCConstants#OPENID_CREDENTIAL} are ignored, as those
     * are handled by their own processors.
     *
     * @return a list of authorization details, or an empty list if none are present.
     */
    public static List<OID4VCAuthorizationDetail> getAuthorizationDetails(AccessTokenResponse response) {
        return Optional.ofNullable(response.getAuthorizationDetails()).orElse(List.of()).stream()
                .filter(authzResponse -> OID4VCConstants.OPENID_CREDENTIAL.equals(authzResponse.getType()))
                // using a type narrowing value conversion should be sufficient here
                .map(authzResponse -> JsonSerialization.mapper.convertValue(authzResponse, OID4VCAuthorizationDetail.class))
                .toList();
    }

    /**
     * Access AuthorizationDetails from the AccessToken JWT
     * rather than from the Token endpoint response
     */
    public static List<OID4VCAuthorizationDetail> getAuthorizationDetailsFromAccessToken(String accessToken) {
        String tokenContent;
        try {
            tokenContent = new JWSInput(accessToken).readContentAsString();
        } catch (JWSInputException e) {
            throw new IllegalArgumentException("Cannot parse access token", e);
        }
        LinkedHashMap<?, ?> contentMap = JsonSerialization.valueFromString(tokenContent, LinkedHashMap.class);
        return Optional.ofNullable(contentMap.get(AUTHORIZATION_DETAILS))
                .map(JsonSerialization::valueAsString)
                .map(it -> JsonSerialization.valueFromString(it, OID4VCAuthorizationDetail[].class))
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }
}
