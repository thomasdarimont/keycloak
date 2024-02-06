package org.keycloak.accesscontrol.opa.client;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpaResponse {

    public static final OpaResponse DENY;

    static {
        OpaResponse deny = new OpaResponse();
        deny.setResult(false);
        deny.setMetadata(Collections.emptyMap());
        DENY = deny;
    }

    private Boolean result;

    private String decisionId;

    private Map<String, Object> metadata;

    @JsonIgnore
    public boolean isAllowed() {
        return result == Boolean.TRUE;
    }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @JsonAnySetter
    public void handleUnknownProperty(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    @Override
    public String toString() {
        return "OpaAccessResponse{" + "result=" + result + ", metadata=" + metadata + '}';
    }
}
