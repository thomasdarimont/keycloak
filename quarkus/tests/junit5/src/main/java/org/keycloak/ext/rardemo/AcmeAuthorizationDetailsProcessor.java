package org.keycloak.ext.rardemo;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.rar.AuthorizationDetailsProcessor;
import org.keycloak.protocol.oidc.rar.AuthorizationDetailsProcessorFactory;
import org.keycloak.protocol.oidc.rar.InvalidAuthorizationDetailsException;
import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;
import org.keycloak.util.AbstractAuthorizationDetailsParser;
import org.keycloak.util.JsonSerialization;

import java.util.List;
import java.util.Set;

public class AcmeAuthorizationDetailsProcessor implements AuthorizationDetailsProcessor {

    private final KeycloakSession session;
    private final Parser parser;

    public AcmeAuthorizationDetailsProcessor(KeycloakSession session, Parser parser) {
        this.session = session;
        this.parser = parser;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("acme_booking");
    }

    @Override
    public Class getSupportedResponseJavaType(String type) {
        return AuthorizationDetailsJSONRepresentation.class;
    }

    @Override
    public AuthorizationDetailsJSONRepresentation validateAuthorizationDetail(AuthorizationDetailsJSONRepresentation authzDetail) throws InvalidAuthorizationDetailsException {
        // called when an authorization detail is submitted
        return authzDetail;
    }

    @Override
    public AuthorizationDetailsJSONRepresentation process(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation authorizationDetailsMember) throws InvalidAuthorizationDetailsException {
        // called when an authorization detail is processed
        return authorizationDetailsMember;
    }

    @Override
    public List handleMissingAuthorizationDetails(UserSessionModel userSession, ClientSessionContext clientSessionCtx) throws InvalidAuthorizationDetailsException {
        // here we can return a list of authorization details based on the user session and client session context if necessary
        return List.of();
    }

    @Override
    public AuthorizationDetailsJSONRepresentation processStoredAuthorizationDetails(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation storedAuthDetailsMember) throws InvalidAuthorizationDetailsException {
        // called when an authorization detail is loaded from client session
        return storedAuthDetailsMember;
    }

    @Override
    public void afterAuthorizationDetailsProcessed(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation authorizationDetailsResponse) {
        // allows for post processing after authorization detail is processed
    }

    @Override
    public AuthorizationDetailsJSONRepresentation narrowRepresentation(AuthorizationDetailsJSONRepresentation authzDetail) {
        // here we could return a more specific subtype of AuthorizationDetailsJSONRepresentation
        return parser.parseToSubtype(authzDetail, AuthorizationDetailsJSONRepresentation.class);
    }

    @Override
    public void close() {

    }

    /**
     * Custom factory that can create acme authorization details processor
     */
    public static class Factory implements AuthorizationDetailsProcessorFactory {

        @Override
        public AuthorizationDetailsProcessor<?> create(KeycloakSession session) {
            return new AcmeAuthorizationDetailsProcessor(session, new Parser());
        }

        // Set<String> getSupportedAuthorizationDetailTypes()

        @Override
        public String getId() {
            return "acme-rar";
        }
    }

    /**
     * Custom parser that can convert acme authorization details into subtype
     */
    public static class Parser extends AbstractAuthorizationDetailsParser<AuthorizationDetailsJSONRepresentation> {

        @Override
        public AuthorizationDetailsJSONRepresentation asSubtype(AuthorizationDetailsJSONRepresentation detail, Class<AuthorizationDetailsJSONRepresentation> clazz) {

            if (detail.getType().equals("acme_booking")) {
                // convert generic authorization_details into AcmeBookingConfirmationDetails
                return JsonSerialization.mapper.convertValue(detail, AcmeBookingConfirmationDetails.class);
            }

            return detail;
        }
    }
}
