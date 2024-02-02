package org.keycloak.accesscontrol.opa.client;

import java.util.Map;

public class OpaRequestContext {

    private Map<String, Object> attributes;

    private Map<String, Object> headers;

    public OpaRequestContext(Map<String, Object> attributes, Map<String, Object> headers) {
        this.attributes = attributes;
        this.headers = headers;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }
}
