package org.keycloak.protocol.oauth2.attestation;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.common.util.Time;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;

import java.util.Map;

public class AttestationChallengeEndpoint {

    protected final KeycloakSession session;
    protected final EventBuilder event;

    public AttestationChallengeEndpoint(KeycloakSession session, EventBuilder event) {
        this.session = session;
        this.event = event;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response challenge() {

        AttestationChallengeResponse challengeResponse = new AttestationChallengeResponse();
        String attestationChallenge = AttestationChallenge.generateChallenge();
        challengeResponse.setAttestationChallenge(attestationChallenge);

        RealmModel realm = session.getContext().getRealm();
        SingleUseObjectProvider singleUse = session.getProvider(SingleUseObjectProvider.class);
        singleUse.put(AttestationChallenge.generateChallengeKey(realm, attestationChallenge), 60, Map.of("ts", String.valueOf(Time.currentTime())));

        return Response.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").entity(challengeResponse).build();
    }
}
