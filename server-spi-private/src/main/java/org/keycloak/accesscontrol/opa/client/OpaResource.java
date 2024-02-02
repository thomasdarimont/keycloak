package org.keycloak.accesscontrol.opa.client;

import java.util.Map;

public class OpaResource {

    private String realm;

    private Map<String, Object> realmAttributes;

    private String clientId;

    private Map<String, Object> clientAttributes;

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public Map<String, Object> getRealmAttributes() {
        return realmAttributes;
    }

    public void setRealmAttributes(Map<String, Object> realmAttributes) {
        this.realmAttributes = realmAttributes;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Map<String, Object> getClientAttributes() {
        return clientAttributes;
    }

    public void setClientAttributes(Map<String, Object> clientAttributes) {
        this.clientAttributes = clientAttributes;
    }
}
