package org.keycloak.ssf.caep.events;

public class CaepEventType {

    /**
     * See: https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-session-revoked
     */
    public static final String SESSION_REVOKED = "https://schemas.openid.net/secevent/caep/event-type/session-revoked";

    /**
     * See: https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-token-claims-change
     */
    public static final String TOKEN_CLAIMS_CHANGE = "https://schemas.openid.net/secevent/caep/event-type/token-claims-change";

    /**
     * https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-credential-change
     */
    public static final String CREDENTIAL_CHANGE = "https://schemas.openid.net/secevent/caep/event-type/credential-change";

    /**
     * https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-assurance-level-change
     */
    public static final String ASSURANCE_LEVEL_CHANGE = "https://schemas.openid.net/secevent/caep/event-type/assurance-level-change";

    /**
     * https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-device-compliance-change
     */
    public static final String DEVICE_COMPLIANCE_CHANGE = "https://schemas.openid.net/secevent/caep/event-type/device-compliance-change";

    /**
     * https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-session-established
     */
    public static final String SESSION_ESTABLISHED = "https://schemas.openid.net/secevent/caep/event-type/session-established";

    /**
     * https://openid.github.io/sharedsignals/openid-caep-1_0.html#name-session-established
     */
    public static final String SESSION_PRESENTED = "https://schemas.openid.net/secevent/caep/event-type/session-presented";
}
