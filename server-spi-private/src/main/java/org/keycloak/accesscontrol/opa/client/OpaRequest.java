package org.keycloak.accesscontrol.opa.client;

public class OpaRequest {

    private final OpaPolicyQuery input;

    public OpaRequest(OpaPolicyQuery input) {
        this.input = input;
    }

    public OpaPolicyQuery getInput() {
        return input;
    }
}
