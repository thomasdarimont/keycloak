package org.keycloak.protocol.ssf.transmitter;

import org.apache.http.entity.StringEntity;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.util.Map;

public class PushTransmitter {

    public static final String MEDIA_TYPE_SECEVENT_JWT = "application/secevent+jwt";

    private static final String API_KEY = System.getenv("CAEP_DEV_API_KEY");

    private final KeycloakSession session;

    public PushTransmitter(KeycloakSession session) {
        this.session = session;
    }

    public Object transmit(String encodedSecurityEventToken) {

        // TODO make SSF delivery URL configurable
        SimpleHttp simpleHttp = SimpleHttp.doPost("https://ssf.caep.dev/ssf/streams/poll", session);
        simpleHttp.socketTimeOutMillis(500);
        simpleHttp.connectTimeoutMillis(1000);
        simpleHttp.connectionRequestTimeoutMillis(500);
        simpleHttp.header("Authorization", "Bearer " + API_KEY);
        simpleHttp.header("Content-Type", MEDIA_TYPE_SECEVENT_JWT);
        simpleHttp.entity(new StringEntity(encodedSecurityEventToken, "UTF-8"));

        SimpleHttp.Response response = null;
        try {
            response = simpleHttp.asResponse();
            return response.asJson(Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
