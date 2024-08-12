package org.keycloak.protocol.ssf;

import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.jose.jws.DefaultTokenManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.ssf.subjects.ComplexSubjectId;
import org.keycloak.protocol.ssf.subjects.SubjectId;
import org.keycloak.protocol.ssf.transmitter.PushTransmitter;
import org.keycloak.services.Urls;
import org.keycloak.urls.UrlType;

import java.util.Map;
import java.util.UUID;

public class SsfEventProcessor {

    private static final Logger LOG = Logger.getLogger(SsfEventProcessor.class);

    private final KeycloakSession session;

    private final PushTransmitter pushTransmitter;

    public SsfEventProcessor(KeycloakSession session, PushTransmitter pushTransmitter) {
        this.session = session;
        this.pushTransmitter = pushTransmitter;
    }

    public void process(SecurityEvent securityEvent, UserModel user) {

        SecurityEventToken securityEventToken = new SecurityEventToken();
        RealmModel realm = session.getContext().getRealm();
        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);

        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
        securityEventToken.issuer(issuer);
        securityEventToken.id(UUID.randomUUID().toString());
        long now = Time.currentTime();
        securityEventToken.iat(now);

        securityEventToken.addEvent(securityEvent);

        // TODO make used ssf id configurable
        SubjectId subjectId = createSubjectId(user);
        securityEventToken.setSubjectId(subjectId);
        securityEventToken.addAudience("https://thomasdarimont.com/caep/webhook");

        // TODO how to determine TX? request-id?
        securityEventToken.setTxn(getTransactionReference());

        DefaultTokenManager defaultTokenManager = new DefaultTokenManager(session);
        String encoded = defaultTokenManager.encode(securityEventToken);
        LOG.infof("SSF Event: %s", encoded);


        // TODO handle event?
        // push event to receiver
        // or
        // enqueue event for pull based receiver

        Object response = pushTransmitter.transmit(encoded);
        LOG.info("Received response: " + response);
    }

    protected SubjectId createSubjectId(UserModel user) {
        ComplexSubjectId subjectId = new ComplexSubjectId();
        subjectId.setUser(Map.of("id", user.getId()));
        subjectId.setUser(Map.of("email", user.getEmail()));
        subjectId.setUser(Map.of("username", user.getUsername()));
        return subjectId;
    }

    protected String getTransactionReference() {
        // TODO try to resolve txn from request header
        //  session.getContext().getRequestHeaders();
        return UUID.randomUUID().toString();
    }
}
