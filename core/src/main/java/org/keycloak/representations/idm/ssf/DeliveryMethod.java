package org.keycloak.representations.idm.ssf;

import java.net.URI;

public enum DeliveryMethod {
        PUSH_BASED("urn:ietf:rfc:8935")
        , POLL_BASED("urn:ietf:rfc:8936")
        ;

        private final String specUrn;

        DeliveryMethod(String specUrn) {
            this.specUrn = specUrn;
        }

        public String getSpecUrn() {
            return specUrn;
        }

        public URI toUri() {
            return URI.create(specUrn);
        }
    }
