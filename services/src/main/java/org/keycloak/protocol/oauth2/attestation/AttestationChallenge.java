package org.keycloak.protocol.oauth2.attestation;

import org.keycloak.models.RealmModel;

import java.util.UUID;

public class AttestationChallenge {

    public static String generateChallenge() {
        String attestationChallenge = UUID.randomUUID().toString();
        return attestationChallenge;
    }

    public static String generateChallengeKey(RealmModel realm, String attestationChallenge) {
        return "abca::" + realm.getName() + "::" + attestationChallenge;
    }
}
