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
package org.keycloak.protocol.ssf.streams;

import jakarta.ws.rs.core.UriInfo;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ssf.SharedSignalsStreamModel;
import org.keycloak.models.ssf.SharedSignalsStreamStatus;
import org.keycloak.models.ssf.SharedSignalsStreamStatusModel;
import org.keycloak.protocol.ssf.caep.events.SessionRevoked;
import org.keycloak.protocol.ssf.set.SecurityEventToken;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.AddSubjectRequest;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.RemoveSubjectRequest;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.ReplaceSharedSignalsStreamRequest;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.UpdateSharedSignalsStreamRequest;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.UpdateStatusRequestSharedSignals;
import org.keycloak.protocol.ssf.streams.SharedStreamManagementResource.VerificationRequest;
import org.keycloak.protocol.ssf.subjects.EmailSubjectId;
import org.keycloak.representations.idm.ssf.DeliveryMethod;
import org.keycloak.representations.idm.ssf.SharedSignalsStreamRepresentation;
import org.keycloak.services.Urls;
import org.keycloak.urls.UrlType;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SharedSignalStreamProvider {

    private static final Map<String, SharedSignalsStreamModel> STREAMS = new LinkedHashMap<>();

    public List<SharedSignalsStreamModel> findStreams(KeycloakSession session, RealmModel realm) {

        // search streams, return empty List if none could be found

//        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);
//        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
//
//        var stream = new SharedSignalsStreamModel();
//        String streamId = UUID.nameUUIDFromBytes("test123".getBytes(StandardCharsets.UTF_8)).toString();
//        stream.setId(streamId);
//        stream.setIssuer(URI.create(issuer));
//        stream.setAudience("https://tdworkshops.ngrok.dev/receiver");
//        stream.setDescription("Test Stream");
//
//        URI pollUri = URI.create(issuer + "/ssf/streams/" + streamId + "/poll");
//        stream.setDeliveryMethod(DeliveryMethod.POLL_BASED);
//        stream.setEndpointUrl(pollUri);
//
////        URI pushUri = URI.create("https://example.com/ssf/streams/1234/push");
////        var pushDelivery = new PushDeliveryMethod(pushUri);
////        stream1.setDelivery(pushDelivery);
//
//        stream.setEventsSupported(List.of(
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
//        ));
//
//        stream.setEventsDelivered(List.of(
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
//        ));
//
//        stream.setEventsRequested(List.of(
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
//                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
//        ));
//        List<SharedSignalsStreamModel> streams = List.of(stream);
        return STREAMS.values().stream().toList();
    }

    public SharedSignalsStreamModel createStream(KeycloakSession session, RealmModel realm, SharedStreamManagementResource.CreateStreamRequest request) {

        KeycloakContext context = session.getContext();
        ClientModel client = context.getClient();

        UriInfo frontendUriInfo = context.getUri(UrlType.FRONTEND);
        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
        URI issuerUri = URI.create(issuer);

        String streamId = UUID.randomUUID().toString();
        String description = request.getDescription();
        List<URI> eventsRequested = request.getEventsRequested();
        var delivery = request.getDelivery();

        var stream = newStream(realm, client, streamId, issuerUri, description, eventsRequested, context, delivery);

        return stream;
    }

    private SharedSignalsStreamModel newStream(RealmModel realm, ClientModel client, String streamId, URI issuerUri, String description, List<URI> eventsRequested, KeycloakContext context, SharedSignalsStreamRepresentation.AbstractDeliveryMethodRepresentation delivery) {
        var stream = new SharedSignalsStreamModel();
        stream.setId(streamId);
        stream.setDescription(description);
        stream.setEventsRequested(eventsRequested);
        stream.setIssuer(issuerUri);
        stream.setAudience(client.getRootUrl());

        UriInfo adminUriInfo = context.getUri(UrlType.ADMIN);

        if (delivery == null) {
            URI pollUri = createPollEndpointUri(realm, adminUriInfo, streamId);
            stream.setDeliveryMethod(DeliveryMethod.POLL_BASED);
            stream.setEndpointUrl(pollUri);
        } else {
            stream.setDeliveryMethod(delivery.getMethod());
            stream.setEndpointUrl(delivery.getEndpointUrl());
        }

        if (eventsRequested == null) {
            // TODO use default events requested
        }
        stream.setEventsRequested(eventsRequested);

        // TODO determine events supported
        stream.setEventsSupported(getEventsSupported(context.getRealm(), context.getClient()));

        // TODO determine events delivered
        stream.setEventsDelivered(getEventsDelivered(context.getRealm(), context.getClient(), stream));

        // TODO determine default stream status
        stream.setStatus(SharedSignalsStreamStatus.enabled);

        STREAMS.put(streamId, stream);
        return stream;
    }

    protected List<URI> getEventsDelivered(RealmModel realm, ClientModel client, SharedSignalsStreamModel stream) {
        return stream.getEventsDelivered();
    }

    protected List<URI> getEventsSupported(RealmModel realm, ClientModel client) {
        return List.of(
                URI.create("https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
                URI.create("https://schemas.openid.net/secevent/caep/event-type/credential-change")
        );
    }

    protected URI createPollEndpointUri(RealmModel realm, UriInfo adminUriInfo, String streamId) {
        return adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/streams/" + streamId + "/poll").build(realm.getName());
    }

    public SharedSignalsStreamModel getStreamById(KeycloakSession session, RealmModel realm, String streamId) {
        return STREAMS.get(streamId);
    }

    public SharedSignalsStreamModel replaceStream(KeycloakSession session, RealmModel realm, ReplaceSharedSignalsStreamRequest updateRequest) {

        // Lookup existing stream by id
        SharedSignalsStreamModel stream = getStreamById(session, realm, updateRequest.getId());
        if (stream == null) {
            return null;
        }

        // replace existing definition with current configuration
        KeycloakContext context = session.getContext();
        UriInfo frontendUriInfo = context.getUri(UrlType.FRONTEND);
        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
        stream = newStream(realm, context.getClient(), updateRequest.getId(), URI.create(issuer), updateRequest.getDescription(), updateRequest.getEventsRequested(), context, updateRequest.getDelivery());

        // return replaced stream representation
        return stream;
    }

    public SharedSignalsStreamModel updateStream(KeycloakSession session, RealmModel realm, UpdateSharedSignalsStreamRequest updateRequest) {

        // Lookup existing stream by id
        SharedSignalsStreamModel stream = getStreamById(session, realm, updateRequest.getId());
        if (stream == null) {
            return null;
        }

        // Update provided properties if changed
        if (stream.getDescription() != null) {
            stream.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventsRequested() != null) {
            stream.setEventsRequested(updateRequest.getEventsRequested());
        }
        if (updateRequest.getMinVerificationInterval() != null) {
            stream.setMinVerificationInterval(updateRequest.getMinVerificationInterval());
        }

        // return updated stream representation
        return stream;
    }

    public boolean deleteStream(KeycloakSession session, RealmModel realm, String streamId) {

        // lookup stream with streamId
        // delete if found
        // return true

        // if not found or could not delete
        // return false
        return STREAMS.remove(streamId) != null;
    }

    public SharedSignalsStreamStatusModel getStreamStatus(KeycloakSession session, RealmModel realm, String streamId) {

        var stream = getStreamById(session, realm, streamId);
        if (stream == null) {
            return null;
        }

        var statusModel = new SharedSignalsStreamStatusModel();
        statusModel.setId(stream.getId());
        statusModel.setReason(stream.getStatusReason());
        statusModel.setStatus(stream.getStatus());

        return statusModel;
    }

    public SharedSignalsStreamStatusModel updateStreamStatus(KeycloakSession session, RealmModel realm, UpdateStatusRequestSharedSignals updateStatusRequest) {

        var stream = getStreamById(session, realm, updateStatusRequest.getStream_id());
        if (stream == null) {
            return null;
        }

        stream.setStatus(SharedSignalsStreamStatus.valueOf(updateStatusRequest.getStatus().name()));
        stream.setStatusReason(updateStatusRequest.getReason());

        // TODO update status

        var statusModel = new SharedSignalsStreamStatusModel();
        statusModel.setReason(updateStatusRequest.getReason());
        statusModel.setId(stream.getId());
        statusModel.setStatus(stream.getStatus());

        return statusModel;
    }

    public boolean addStreamSubject(KeycloakSession session, RealmModel realm, AddSubjectRequest addSubjectRequest) {

        SharedSignalsStreamModel stream = getStreamById(session, realm, addSubjectRequest.getStreamId());
        if (stream == null) {
            return false;
        }
        // resolve stream by streamid
        // resolve user from subject
        // register user to streams
        // return true

        // if not found or could not register
        // return false

        return true;
    }

    public boolean removeSubject(KeycloakSession session, RealmModel realm, RemoveSubjectRequest removeSubjectRequest) {
        // resolve stream by streamid
        SharedSignalsStreamModel stream = getStreamById(session, realm, removeSubjectRequest.getStreamId());
        if (stream == null) {
            return false;
        }
        // resolve user from subject
        // unregister user to streams
        // return true

        // if not found or could not unregister
        // return false
        return true;
    }

    public boolean addVerificationEvent(KeycloakSession session, RealmModel realm, VerificationRequest verificationRequest) {
        // resolve stream by streamid
        SharedSignalsStreamModel stream = getStreamById(session, realm, verificationRequest.getStreamId());
        if (stream == null) {
            return false;
        }
        // extract state
        // if state present add to receiver event, see: https://openid.net/specs/openid-sharedsignals-framework-1_0.html#figure-42
        // trigger verification event for receiver in stream
        return true;
    }

    public List<SecurityEventToken> retrieveEvents(KeycloakSession session, RealmModel realm, String streamId) {

        SharedSignalsStreamModel stream = getStreamById(session, realm, streamId);
        if (stream == null) {
            return Collections.emptyList();
        }

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
