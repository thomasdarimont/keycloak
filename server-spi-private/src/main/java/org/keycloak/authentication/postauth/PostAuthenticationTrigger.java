package org.keycloak.authentication.postauth;

/**
 * The point at which post-authentication actions are executed.
 */
public enum PostAuthenticationTrigger {

    /** Browser based login where the user just authenticated with credentials. */
    BROWSER_LOGIN(true),

    /** Browser based login into a client via an existing SSO session. */
    BROWSER_SSO(true),

    /**
     * Browser based login where the user just authenticated through an identity provider (brokering), including
     * the first login that links or creates the local user. The provider alias is available on the context.
     */
    BROKER_LOGIN(true),

    /** Resource owner password credentials grant. */
    DIRECT_GRANT(false),

    /** Client credentials grant, the subject is the service account user of the client. */
    CLIENT_CREDENTIALS(false),

    /** Refresh token grant, online and offline. */
    TOKEN_REFRESH(false);

    private final boolean interactive;

    PostAuthenticationTrigger(boolean interactive) {
        this.interactive = interactive;
    }

    /**
     * @return {@code true} if a browser is involved and an action may render a challenge or add required actions.
     */
    public boolean isInteractive() {
        return interactive;
    }
}
