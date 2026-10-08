package org.keycloak.authentication.clientaccess;

public final class ClientAccessDecision {

    private static final ClientAccessDecision GRANTED = new ClientAccessDecision(true, null, null);

    private final boolean granted;
    private final String policyName;
    private final String reason;

    private ClientAccessDecision(boolean granted, String policyName, String reason) {
        this.granted = granted;
        this.policyName = policyName;
        this.reason = reason;
    }

    public static ClientAccessDecision granted() {
        return GRANTED;
    }

    public static ClientAccessDecision denied(String policyName, String reason) {
        return new ClientAccessDecision(false, policyName, reason);
    }

    public boolean isGranted() {
        return granted;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getReason() {
        return reason;
    }
}
