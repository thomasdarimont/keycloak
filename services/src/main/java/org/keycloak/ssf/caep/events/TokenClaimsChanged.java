package org.keycloak.ssf.caep.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.ssf.SecurityEvent;

import java.util.Map;

/**
 * Token Claims Change signals that a claim in a token, identified by the subject claim, has changed.
 */
public class TokenClaimsChanged extends SecurityEvent {

    /**
     * One or more claims with their new value(s)
     */
    @JsonProperty("claims")
    protected Map<String, Object> claims;

    public TokenClaimsChanged() {
        super(CaepEventType.TOKEN_CLAIMS_CHANGE);
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims;
    }
}
