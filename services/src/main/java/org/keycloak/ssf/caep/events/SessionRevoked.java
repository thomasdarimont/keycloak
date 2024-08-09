package org.keycloak.ssf.caep.events;

import org.keycloak.ssf.SecurityEvent;

/**
 * Session Revoked signals that the session identified by the subject has been revoked. The explicit session identifier may be directly referenced in the subject or other properties of the session may be included to allow the receiver to identify applicable sessions.
 */
public class SessionRevoked extends SecurityEvent {

    public SessionRevoked() {
        super(CaepEventType.SESSION_REVOKED);
    }
}
