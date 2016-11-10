package org.keycloak.services.resources;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.OAuthErrorException;
import org.keycloak.RSATokenVerifier;
import org.keycloak.common.ClientConnection;
import org.keycloak.common.VerificationException;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Providers;
import java.util.List;

/**
 * Service for validating credentials after authentication.
 *
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class CredentialValidationService {

    private RealmModel realm;

    @Context
    private HttpRequest request;

    @Context
    protected HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    @Context
    private ClientConnection clientConnection;

    @Context
    protected Providers providers;

    @Context
    protected KeycloakSession session;

    private final EventBuilder event;

    private final AppAuthManager appAuthManager;

    public CredentialValidationService(RealmModel realm, EventBuilder event) {
        this.realm = realm;
        this.event = event.event(EventType.VERIFY_TOTP);
        this.appAuthManager = new AppAuthManager();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateCredentials(List<CredentialRepresentation> credentialRepresentations) {

        String accessTokenString = this.appAuthManager.extractAuthorizationHeaderToken(request.getHttpHeaders());

        if (accessTokenString == null) {
            event.error(Errors.INVALID_TOKEN);
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Token not provided", Response.Status.BAD_REQUEST);
        }

        AccessToken accessToken = verifyToken(accessTokenString, event);

        UserSessionModel userSession = session.sessions().getUserSession(realm, accessToken.getSessionState());

        if (userSession == null) {
            event.error(Errors.USER_SESSION_NOT_FOUND);
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "User session not found", Response.Status.BAD_REQUEST);
        }
        event.session(userSession);

        UserModel userModel = userSession.getUser();
        if (userModel == null) {
            event.error(Errors.USER_NOT_FOUND);
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "User not found", Response.Status.BAD_REQUEST);
        }
        event.user(userModel)
                .detail(Details.USERNAME, userModel.getUsername());

        ClientSessionModel clientSession = session.sessions().getClientSession(accessToken.getClientSession());
        if (clientSession == null || !AuthenticationManager.isSessionValid(realm, userSession)) {
            event.error(Errors.SESSION_EXPIRED);
            throw new ErrorResponseException(OAuthErrorException.INVALID_TOKEN, "Session expired", Response.Status.UNAUTHORIZED);
        }

        ClientModel clientModel = realm.getClientByClientId(accessToken.getIssuedFor());
        if (clientModel == null) {
            event.error(Errors.CLIENT_NOT_FOUND);
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Client not found", Response.Status.BAD_REQUEST);
        }
        event.client(clientModel);

        if (!clientModel.isEnabled()) {
            event.error(Errors.CLIENT_DISABLED);
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Client disabled", Response.Status.BAD_REQUEST);
        }

        UserModel user = session.users().getUserById(accessToken.getSubject(), realm);

        if (user == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        boolean allValid = validateCredentials(credentialRepresentations, user);

        if (allValid) {
            event.success();
            Response.ResponseBuilder responseBuilder = Response.ok();
            return Cors.add(request, responseBuilder).auth().allowedOrigins(accessToken).build();
        }

        event.error(Errors.INVALID_REQUEST);

        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    private AccessToken verifyToken(String accessToken, EventBuilder event) {
        try {
            RSATokenVerifier verifier = RSATokenVerifier.create(accessToken)
                    .realmUrl(Urls.realmIssuer(uriInfo.getBaseUri(), realm.getName()));
            String kid = verifier.getHeader().getKeyId();
            verifier.publicKey(session.keys().getPublicKey(realm, kid));
            return verifier.verify().getToken();
        } catch (VerificationException e) {
            event.error(Errors.INVALID_TOKEN);
            throw new ErrorResponseException(OAuthErrorException.INVALID_TOKEN, "Token invalid: " + e.getMessage(), Response.Status.UNAUTHORIZED);
        }
    }

    private boolean validateCredentials(List<CredentialRepresentation> credentialRepresentations, UserModel user) {

        boolean allValid = true;
        for (CredentialRepresentation credentialRepresentation : credentialRepresentations) {

            UserCredentialModel credentials = new UserCredentialModel();
            credentials.setType(credentialRepresentation.getType());
            credentials.setValue(credentialRepresentation.getValue());
            boolean credentialValid = session.userCredentialManager().isValid(realm, user, credentials);

            allValid &= credentialValid;
        }

        return allValid;
    }
}
