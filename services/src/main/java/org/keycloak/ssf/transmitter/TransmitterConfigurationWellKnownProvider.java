package org.keycloak.ssf.transmitter;

import org.keycloak.models.KeycloakSession;
import org.keycloak.wellknown.WellKnownProvider;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class TransmitterConfigurationWellKnownProvider implements WellKnownProvider {

    private final KeycloakSession session;

    public TransmitterConfigurationWellKnownProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getConfig() {
        return Map.ofEntries( //
                entry("spec_version", "1_0"), //
                entry("issuer", "https://issuer/ssf/xxx"), //
                entry("jwks_uri", "https://issuer/jwks"), //
                entry("delivery_methods_supported", List.of("urn:ietf:rfc:8935", "urn:ietf:rfc:8936")), //
                entry("configuration_endpoint", "https://issuer/ssf/config_endpoint"), //
                entry("status_endpoint", "https://issuer/ssf/status_endpoint"), //
                entry("add_subject_endpoint", "https://issuer/ssf/add_subject_endpoint"), //
                entry("remove_subject_endpoint", "https://issuer/ssf/remove_subject_endpoint"), //
                entry("verification_endpoint", "https://issuer/ssf/verification_endpoint"), //
                entry("critical_subject_members", List.of("tenant", "user")), //
                entry("authorization_schemes", List.of( //
                        Map.of("spec_urn", "urn:ietf:rfc:6749"), //
                        Map.of("spec_urn", "urn:ietf:rfc:8705")
                )), //
                entry("default_subjects", List.of("NONE")) // ALL/NONE
        );
    }

    @Override
    public void close() {
    }
}
