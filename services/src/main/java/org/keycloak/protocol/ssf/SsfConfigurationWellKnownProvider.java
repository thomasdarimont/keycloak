package org.keycloak.protocol.ssf;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.keycloak.services.Urls;
import org.keycloak.services.resources.RealmsResource;
import org.keycloak.urls.UrlType;
import org.keycloak.wellknown.WellKnownProvider;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class SsfConfigurationWellKnownProvider implements WellKnownProvider {

    private final KeycloakSession session;

    public SsfConfigurationWellKnownProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getConfig() {

        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);
        UriInfo backendUriInfo = session.getContext().getUri(UrlType.BACKEND);
        UriBuilder backendUriBuilder = RealmsResource.protocolUrl(backendUriInfo);

        RealmModel realm = session.getContext().getRealm();

        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
        URI jwksUri = backendUriBuilder.clone().path(OIDCLoginProtocolService.class, "certs")
                .build(realm.getName(), OIDCLoginProtocol.LOGIN_PROTOCOL);

        String specVersion = "1_0-ID2";
        List<String> deliveryMethods = List.of(DeliveryMethod.PUSH_BASED.getSpecUrn(), DeliveryMethod.POLL_BASED.getSpecUrn());

        Map<String, Object> config = Map.ofEntries( //
                  entry("spec_version", specVersion) //
                , entry("issuer", issuer) //
                , entry("jwks_uri", jwksUri.toString()) //
                , entry("delivery_methods_supported", deliveryMethods) //
                /*
                entry("configuration_endpoint", "https://issuer/ssf/config_endpoint"), //
                entry("status_endpoint", "https://issuer/ssf/status_endpoint"), //
                entry("add_subject_endpoint", "https://issuer/ssf/add_subject_endpoint"), //
                entry("remove_subject_endpoint", "https://issuer/ssf/remove_subject_endpoint"), //
                entry("verification_endpoint", "https://issuer/ssf/verification_endpoint"), //
                entry("critical_subject_members", List.of("tenant", "user")), //
                entry("default_subjects", List.of("NONE")) // ALL/NONE
                */
                , entry("authorization_schemes", List.of( //
                        Map.of("spec_urn", AuthorizationScheme.OAUTH.getSpecUrn()), //
                        Map.of("spec_urn", AuthorizationScheme.OAUTH_MTLS.getSpecUrn())
                )) //
        );
        return config;
    }

    @Override
    public void close() {
    }

    public enum AuthorizationScheme{

        OAUTH("urn:ietf:rfc:6749")
        ,OAUTH_MTLS("urn:ietf:rfc:8705");

        private final String specUrn;

        AuthorizationScheme(String specUrn) {
            this.specUrn = specUrn;
        }

        public String getSpecUrn() {
            return specUrn;
        }
    }

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
    }
}
