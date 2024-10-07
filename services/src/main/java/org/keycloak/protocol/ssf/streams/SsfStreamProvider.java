package org.keycloak.protocol.ssf.streams;

import jakarta.ws.rs.core.UriInfo;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.ssf.caep.events.SessionRevoked;
import org.keycloak.protocol.ssf.set.SecurityEventToken;
import org.keycloak.protocol.ssf.subjects.EmailSubjectId;
import org.keycloak.services.Urls;
import org.keycloak.urls.UrlType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class SsfStreamProvider {

    public List<StreamRepresentation> findStreams(KeycloakSession session, RealmModel realm) {

        // search streams, return empty List if none could be found

        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);
        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());

        var stream = new StreamRepresentation();
        String streamId = UUID.nameUUIDFromBytes("test123".getBytes(StandardCharsets.UTF_8)).toString();
        stream.setId(streamId);
        stream.setIssuer(URI.create(issuer));
        stream.setAudience("https://tdworkshops.ngrok.dev/receiver");
        stream.setDescription("Test Stream");

        URI pollUri = URI.create(issuer + "/ssf/streams/" + streamId + "/poll");
        var pollDelivery = new StreamRepresentation.PollDeliveryMethod(pollUri);
        stream.setDelivery(pollDelivery);

//        URI pushUri = URI.create("https://example.com/ssf/streams/1234/push");
//        var pushDelivery = new PushDeliveryMethod(pushUri);
//        stream1.setDelivery(pushDelivery);

        stream.setEventsSupported(List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        ));

        stream.setEventsDelivered(List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        ));

        stream.setEventsRequested(List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        ));
        List<StreamRepresentation> streams = List.of(stream);
        return streams;
    }

    public StreamRepresentation createStream(KeycloakSession session, RealmModel realm, StreamManagementResource.CreateStreamRequest request) {

        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);
        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());

        var stream = new StreamRepresentation();
        String streamId = UUID.nameUUIDFromBytes("test123".getBytes(StandardCharsets.UTF_8)).toString();
        stream.setId(streamId);
        stream.setIssuer(URI.create(issuer));
        stream.setAudience("https://tdworkshops.ngrok.dev/receiver");

        stream.setDescription(request.getDescription());

        UriInfo adminUriInfo = session.getContext().getUri(UrlType.ADMIN);

        StreamRepresentation.AbstractDeliveryMethod deliveryMethod;
        if (request.getDelivery() == null) {
            URI pollUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/streams/" + streamId + "/poll").build(realm.getName());
            deliveryMethod = new StreamRepresentation.PollDeliveryMethod(pollUri);
        } else {
            deliveryMethod = request.getDelivery();
        }
        stream.setDelivery(deliveryMethod);


        List<URI> eventsRequested = request.getEventsRequested();
        if (eventsRequested == null) {
            // TODO use default events requested
        }
        stream.setEventsRequested(eventsRequested);

        // TODO determine events supported
        stream.setEventsSupported(List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        ));

        // TODO determine events delivered
        stream.setEventsDelivered(List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        ));

        return stream;
    }

    public StreamRepresentation findStream(KeycloakSession session, RealmModel realm, String streamId) {
        return null;
    }

    public StreamRepresentation updateStream(KeycloakSession session, RealmModel realm, StreamManagementResource.UpdateStreamRequest updateRequest) {

        if (updateRequest instanceof StreamManagementResource.ReplaceStreamRequest) {
            // replace eixsting definition with current configuration
        } else {

            // Lookup existing stream by id
            // Update provided properties if changed
        }

        // return new stream representation

        return null;
    }

    public boolean deleteStream(KeycloakSession session, RealmModel realm, String streamId) {

        // lookup stream with streamId
        // delete if found
        // return true

        // if not found or could not delete
        // return false

        return true;
    }

    public StreamManagementResource.StreamStatusRepresentation getStreamStatus(KeycloakSession session, RealmModel realm, String streamId) {

        // fetch stream
        // compute status

        StreamManagementResource.StreamStatusRepresentation streamStatusRepresentation = new StreamManagementResource.StreamStatusRepresentation();
        return streamStatusRepresentation;
    }

    public StreamManagementResource.StreamStatusRepresentation updateStreamStatus(StreamManagementResource.UpdateStatusRequest updateStatusRequest) {
        // fetch stream
        // update status
        StreamManagementResource.StreamStatusRepresentation streamStatusRepresentation = new StreamManagementResource.StreamStatusRepresentation();
        return streamStatusRepresentation;
    }

    public boolean addStreamSubject(KeycloakSession session, RealmModel realm, StreamManagementResource.AddSubjectRequest addSubjectRequest) {

        // resolve stream by streamid
        // resolve user from subject
        // register user to streams
        // return true

        // if not found or could not register
        // return false

        return true;
    }

    public boolean removeSubject(KeycloakSession session, RealmModel realm, StreamManagementResource.RemoveSubjectRequest removeSubjectRequest) {
        // resolve stream by streamid
        // resolve user from subject
        // unregister user to streams
        // return true

        // if not found or could not unregister
        // return false
        return true;
    }

    public boolean addVerificationEvent(KeycloakSession session, RealmModel realm, StreamManagementResource.VerificationRequest verificationRequest) {
        // resolve stream by streamid
        // extract state
        // if state present add to receiver event, see: https://openid.net/specs/openid-sharedsignals-framework-1_0.html#figure-42
        // trigger verification event for receiver in stream
        return true;
    }

    public List<SecurityEventToken> retrieveEvents(KeycloakSession session, RealmModel realm, String streamId) {
        SecurityEventToken token = new SecurityEventToken();
        token.setTxn("1234");
        EmailSubjectId subjectId = new EmailSubjectId();
        subjectId.setEmail("tester@acme.local");
        token.setSubjectId(subjectId);
        SessionRevoked sessionRevoked = new SessionRevoked();
        sessionRevoked.setEventTimestamp(1615304991643L);
        token.addEvent(sessionRevoked);
        return List.of(token);
    }
}
