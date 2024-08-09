package org.keycloak.ssf.subjects;

public class EmailSubjectId extends SubjectId {

    public static final String TYPE = "email";

    protected String email;

    public EmailSubjectId() {
        super(TYPE);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
