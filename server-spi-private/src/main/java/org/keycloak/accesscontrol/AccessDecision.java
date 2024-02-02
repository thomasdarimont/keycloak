package org.keycloak.accesscontrol;

import java.util.Map;

public class AccessDecision {

    public enum Outcome {
        ALLOWED, DENIED
    }

    private final Outcome outcome;

    private final Map<String, Object> details;

    private final String message;

    public AccessDecision(boolean allow, Map<String, Object> details, String message) {
        this(allow ? Outcome.ALLOWED : Outcome.DENIED, details, message);
    }

    public AccessDecision(Outcome outcome, Map<String, Object> details, String message) {
        this.outcome = outcome;
        this.details = details;
        this.message = message;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAllowed() {
        return outcome == Outcome.ALLOWED;
    }

    public boolean isDenied() {
        return outcome == Outcome.DENIED;
    }

    @Override
    public String toString() {
        return "AccessDecision{" +
                "outcome=" + outcome +
                ", details=" + details +
                '}';
    }
}
