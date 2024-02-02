package org.keycloak.accesscontrol.opa.client;

import java.util.List;
import java.util.Map;

public class OpaSubject {

    private String id;

    private String username;

    private List<String> realmRoles;

    private List<String> clientRoles;

    private Map<String, Object> attributes;

    private List<String> groups;

    public OpaSubject() {
    }

    public OpaSubject(String username, List<String> realmRoles, List<String> clientRoles, Map<String, Object> attributes, List<String> groups) {
        this.username = username;
        this.realmRoles = realmRoles;
        this.clientRoles = clientRoles;
        this.attributes = attributes;
        this.groups = groups;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(List<String> realmRoles) {
        this.realmRoles = realmRoles;
    }

    public List<String> getClientRoles() {
        return clientRoles;
    }

    public void setClientRoles(List<String> clientRoles) {
        this.clientRoles = clientRoles;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
}
