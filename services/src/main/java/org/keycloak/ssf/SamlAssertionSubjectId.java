package org.keycloak.ssf;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.ssf.subjects.SubjectId;

public class SamlAssertionSubjectId extends SubjectId {

    public static final String EVENT_TYPE = "saml_assertion_id";

    @JsonProperty("issuer")
    protected String issuer;

    @JsonProperty("assertion_id")
    protected String assertionId;

    public SamlAssertionSubjectId(String format) {
        super(EVENT_TYPE);
    }
}
