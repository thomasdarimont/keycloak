package org.keycloak.authentication.clientaccess;

public final class ClientAccessPolicyResult {

    private static final ClientAccessPolicyResult GRANTED = new ClientAccessPolicyResult(true, null);

    private final boolean granted;
    private final String failedCondition;

    private ClientAccessPolicyResult(boolean granted, String failedCondition) {
        this.granted = granted;
        this.failedCondition = failedCondition;
    }

    public static ClientAccessPolicyResult granted() {
        return GRANTED;
    }

    public static ClientAccessPolicyResult denied(String failedCondition) {
        return new ClientAccessPolicyResult(false, failedCondition);
    }

    public boolean isGranted() {
        return granted;
    }

    /** @return name of the first condition that did not match, {@code null} if granted */
    public String getFailedCondition() {
        return failedCondition;
    }
}
