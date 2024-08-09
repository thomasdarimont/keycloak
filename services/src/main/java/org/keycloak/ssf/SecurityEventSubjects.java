package org.keycloak.ssf;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SecurityEventSubjects {

    /**
     * The user involved with the event
     */
    @JsonProperty("user")
    protected Map<String, String> user;

    /**
     * The device involved with the event
     */
    @JsonProperty("device")
    protected Map<String, String> device;

    /**
     * The session involved with the event
     */
    @JsonProperty("session")
    protected Map<String, String> session;

    /**
     * The application involved with the event
     */
    @JsonProperty("application")
    protected Map<String, String> application;

    /**
     * The tenant involved with the event
     */
    @JsonProperty("tenant")
    protected Map<String, String> tenant;

    /**
     * The org_unit involved with the event
     */
    @JsonProperty("org_unit")
    protected Map<String, String> orgUnit;

    /**
     * The group involved with the event
     */
    @JsonProperty("group")
    protected Map<String, String> group;
}
