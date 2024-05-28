package org.keycloak.ssf;

import java.util.Map;

public class SecurityEventSubjects {

    /**
     * The device involved with the event
     */
    protected Map<String, String> device;

    /**
     * The tenant involved with the event
     */
    protected Map<String, String> tenant;

    /**
     * The user involved with the event
     */
    protected Map<String, String> user;

    public Map<String, String> getDevice() {
        return device;
    }

    public void setDevice(Map<String, String> device) {
        this.device = device;
    }

    public Map<String, String> getTenant() {
        return tenant;
    }

    public void setTenant(Map<String, String> tenant) {
        this.tenant = tenant;
    }

    public Map<String, String> getUser() {
        return user;
    }

    public void setUser(Map<String, String> user) {
        this.user = user;
    }
}
