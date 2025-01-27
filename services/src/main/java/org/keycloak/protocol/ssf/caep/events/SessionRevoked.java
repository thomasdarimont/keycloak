package org.keycloak.protocol.ssf.caep.events;

import org.keycloak.protocol.ssf.SecurityEvent;

/**
 * Session Revoked signals that the session identified by the subject has been revoked. The explicit session identifier may be directly referenced in the subject or other properties of the session may be included to allow the receiver to identify applicable sessions.
 */
public class SessionRevoked extends SecurityEvent {

    /**
     * See: https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-session-revoked
     */
    public static final String TYPE = "https://schemas.openid.net/secevent/caep/event-type/session-revoked";

    public SessionRevoked() {
        super(TYPE);
    }
}
