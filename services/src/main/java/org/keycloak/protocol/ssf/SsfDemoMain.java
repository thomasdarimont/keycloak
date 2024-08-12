package org.keycloak.protocol.ssf;

import org.keycloak.protocol.ssf.caep.events.SessionEstablished;
import org.keycloak.protocol.ssf.caep.events.SessionPresented;
import org.keycloak.protocol.ssf.caep.events.SessionRevoked;
import org.keycloak.protocol.ssf.subjects.EmailSubjectId;
import org.keycloak.protocol.ssf.subjects.IssuerSubjectId;
import org.keycloak.protocol.ssf.subjects.SubjectId;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.Set;

public class SsfDemoMain {

    public static void main(String[] args) throws IOException {

        SecurityEventToken token = new SecurityEventToken();
        token.issuer("https://idp.example.com/123456789/");
        token.id("24c63fb56e5a2d77a6b512616ca9fa24");
        token.iat(1615305159L);
        token.audience("https://sp.example.com/caep");
        token.txn("8675309");
        SubjectId subjectId = new EmailSubjectId() {
            {
                setEmail("tester@example.com");
            }
        };
        token.subjectId(subjectId);

        SessionEstablished sessionEstablished = new SessionEstablished();
        sessionEstablished.setIps(Set.of("192.168.1.12", "10.1.1.1"));
        sessionEstablished.setFingerPrintUserAgent("abb0b6e7da81a42233f8f2b1a8ddb1b9a4c81611");
        sessionEstablished.setAcr("AAL2");
        sessionEstablished.setAmr("otp");
        sessionEstablished.setEventTimestamp(1615304991643L);
        token.addEvent(sessionEstablished);

        SessionPresented sessionPresented = new SessionPresented();
        sessionPresented.setIps(Set.of("192.168.1.12", "10.1.1.1"));
        sessionPresented.setFingerPrintUserAgent("abb0b6e7da81a42233f8f2b1a8ddb1b9a4c81611");
        sessionPresented.setExtId("12345");
        sessionPresented.setEventTimestamp(1615304991643L);
        token.addEvent(sessionPresented);

        SessionRevoked sessionRevoked = new SessionRevoked();
        sessionRevoked.setEventTimestamp(1615304991643L);
        token.addEvent(sessionRevoked);

        String json = JsonSerialization.writeValueAsPrettyString(token);
        System.out.println(json);

    }
}
