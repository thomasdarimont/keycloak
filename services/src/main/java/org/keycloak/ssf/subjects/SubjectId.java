package org.keycloak.ssf.subjects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubjectId {

    @JsonProperty("format")
    protected final String format;

    public SubjectId(String format) {
        this.format = format;
    }
}
