package org.keycloak.accesscontrol.opa.client;

public class OpaPolicyQuery {

    private OpaSubject subject;

    private OpaResource resource;

    private OpaRequestContext context;

    private String action;

    public OpaSubject getSubject() {
        return subject;
    }

    public void setSubject(OpaSubject subject) {
        this.subject = subject;
    }

    public OpaResource getResource() {
        return resource;
    }

    public void setResource(OpaResource resource) {
        this.resource = resource;
    }

    public OpaRequestContext getContext() {
        return context;
    }

    public void setContext(OpaRequestContext context) {
        this.context = context;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "OpaAccessPolicyRequest{" + "subject=" + subject + ", resource=" + resource + ", context=" + context + ", action='" + action + '\'' + '}';
    }
}
