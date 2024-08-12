package org.keycloak.protocol.ssf.subjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Subject Identifier is structured information that describes a subject related to a security event, using named
 * formats to define its encoding as JSON objects within Security Event Tokens.
 *
 * See: https://datatracker.ietf.org/doc/html/rfc9493
 */
public abstract class SubjectId {

    @JsonProperty("format")
    protected final String format;

    public SubjectId(String format) {
        this.format = format;
    }

}
