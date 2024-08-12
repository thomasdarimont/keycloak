package org.keycloak.protocol.ssf.subjects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * See: https://openid.net/specs/openid-sse-framework-1_0.html#sub-id-saml-assertion-id
 */
public class SamlAssertionSubjectId extends SubjectId {

    public static final String TYPE = "saml_assertion_id";

    @JsonProperty("issuer")
    protected String issuer;

    @JsonProperty("assertion_id")
    protected String assertionId;

    public SamlAssertionSubjectId(String format) {
        super(TYPE);
    }
}
