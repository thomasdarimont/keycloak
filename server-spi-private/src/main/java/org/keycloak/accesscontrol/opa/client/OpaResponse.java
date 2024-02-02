package org.keycloak.accesscontrol.opa.client;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.keycloak.accesscontrol.opa.OpaAccessPolicyProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpaResponse {

    public static final OpaResponse DENY;

    static {
        Map<String, Object> result = new HashMap<>();
        result.put("allow", false);
        result.put("message", OpaAccessPolicyProvider.ACCESS_DENIED_MESSAGE);
        DENY = new OpaResponse(result);
    }

    private Map<String, Object> result;

    private Map<String, Object> metadata;

    public OpaResponse() {
        this(Collections.emptyMap());
    }

    public OpaResponse(Map<String, Object> result) {
        this.result = result;
    }

    @JsonIgnore
    public boolean isAllowed() {
        return result != null && Boolean.parseBoolean(String.valueOf(result.get("allow")));
    }

    @JsonIgnore
    public String getMessage() {
        if (result == null) {
            return null;
        }
        Object hint = result.get("message");
        if (!(hint instanceof String)) {
            return null;
        }
        return (String) hint;
    }

    @JsonAnySetter
    public void handleUnknownProperty(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "OpaAccessResponse{" +
                "result=" + result +
                ", metadata=" + metadata +
                '}';
    }
}
