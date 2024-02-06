package org.keycloak.accesscontrol.opa.client;

import org.jboss.logging.Logger;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;

import static org.keycloak.util.JsonSerialization.writeValueAsPrettyString;

public class OpaClient {

    private static final Logger log = Logger.getLogger(OpaClient.class);

    private final KeycloakSession session;

    public OpaClient(KeycloakSession session) {
        this.session = session;
    }

    public OpaResponse evaluatePolicy(String policyUrl, OpaRequest opaRequest) {

        if (log.isDebugEnabled()) {
            try {
                log.debugf("Sending policy request. policyUrl=%s\n%s", //
                        policyUrl, writeValueAsPrettyString(opaRequest));
            } catch (IOException ioe) {
                log.warnf(ioe, "Failed to prepare policy request");
            }
        }

        OpaResponse response = callOpa(policyUrl, opaRequest);

        if (log.isDebugEnabled()) {
            try {
                log.debugf("Received policy response. allowed=%s\n%s", //
                        response.isAllowed(), writeValueAsPrettyString(response));
            } catch (IOException ioe) {
                log.warnf(ioe, "Failed to process policy response");
            }
        }

        return response;
    }

    protected OpaResponse callOpa(String policyUrl, OpaRequest opaRequest) {

        SimpleHttp http = SimpleHttp.doPost(policyUrl, session);
        http.json(opaRequest);

        try {
            try (SimpleHttp.Response response = http.asResponse()) {
                OpaResponse opaResponse = response.asJson(OpaResponse.class);
                return opaResponse;
            }
        } catch (IOException e) {
            log.errorf(e, "Policy request failed");
            return OpaResponse.DENY;
        }
    }

}
