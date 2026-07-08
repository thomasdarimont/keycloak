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
        return authzDetail;
    }

    @Override
    public AuthorizationDetailsJSONRepresentation process(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation authorizationDetailsMember) throws InvalidAuthorizationDetailsException {
        return authorizationDetailsMember;
    }

    @Override
    public List handleMissingAuthorizationDetails(UserSessionModel userSession, ClientSessionContext clientSessionCtx) throws InvalidAuthorizationDetailsException {
        return List.of();
    }

    @Override
    public AuthorizationDetailsJSONRepresentation processStoredAuthorizationDetails(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation storedAuthDetailsMember) throws InvalidAuthorizationDetailsException {
        return storedAuthDetailsMember;
    }

    @Override
    public void afterAuthorizationDetailsProcessed(UserSessionModel userSession, ClientSessionContext clientSessionCtx, AuthorizationDetailsJSONRepresentation authorizationDetailsResponse) {

    }

    @Override
    public AuthorizationDetailsJSONRepresentation narrowRepresentation(AuthorizationDetailsJSONRepresentation authzDetail) {
        return parser.parseToSubtype(authzDetail, AuthorizationDetailsJSONRepresentation.class);
    }

    @Override
    public void close() {

    }

    public static class Parser extends AbstractAuthorizationDetailsParser<AuthorizationDetailsJSONRepresentation> {

        @Override
        public AuthorizationDetailsJSONRepresentation asSubtype(AuthorizationDetailsJSONRepresentation authzDetail, Class<AuthorizationDetailsJSONRepresentation> clazz) {

            if (authzDetail.getType().equals("acme_booking")) {
                return JsonSerialization.mapper.convertValue(authzDetail, AcmeBookingConfirmationDetails.class);
            }

            return authzDetail;
        }
    }

    public static class Factory implements AuthorizationDetailsProcessorFactory {

        @Override
        public AuthorizationDetailsProcessor<?> create(KeycloakSession session) {
            return new AcmeAuthorizationDetailsProcessor(session, new Parser());
        }

        @Override
        public String getId() {
            return "acme-rar";
        }
    }
}
