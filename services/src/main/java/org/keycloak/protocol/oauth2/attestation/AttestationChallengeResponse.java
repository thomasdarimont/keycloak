package org.keycloak.protocol.oauth2.attestation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttestationChallengeResponse {

    @JsonProperty("attestation_challenge")
    protected String attestationChallenge;

    public String getAttestationChallenge() {
        return attestationChallenge;
    }

    public void setAttestationChallenge(String attestationChallenge) {
        this.attestationChallenge = attestationChallenge;
    }
}
