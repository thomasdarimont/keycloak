package org.keycloak.authentication.postauth;

/**
 * Outcome of a single post-authentication action.
 */
public final class PostAuthenticationResult {

    private static final PostAuthenticationResult CONTINUE = new PostAuthenticationResult(false, null, null);

    private final boolean denied;
    private final String reason;
    private final String actionName;

    private PostAuthenticationResult(boolean denied, String reason, String actionName) {
        this.denied = denied;
        this.reason = reason;
        this.actionName = actionName;
    }

    public static PostAuthenticationResult proceed() {
        return CONTINUE;
    }

    public static PostAuthenticationResult deny(String reason) {
        return new PostAuthenticationResult(true, reason, null);
    }

    PostAuthenticationResult withActionName(String name) {
        return denied ? new PostAuthenticationResult(true, reason, name) : this;
    }

    public boolean isDenied() {
        return denied;
    }

    /**
     * @return human readable reason for the denial, never exposed to end users directly
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return name of the configured action instance that produced the denial, {@code null} if not denied
     */
    public String getActionName() {
        return actionName;
    }
}
