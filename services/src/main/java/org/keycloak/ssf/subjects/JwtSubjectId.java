package org.keycloak.ssf.subjects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JwtSubjectId extends SubjectId {

    public static final String TYPE = "jwt_id";

    @JsonProperty("iss")
    protected String iss;

    @JsonProperty("jti")
    protected String jti;

    public JwtSubjectId(String format) {
        super(TYPE);
    }

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }
}
