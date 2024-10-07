package org.keycloak.protocol.ssf.transmitter;

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
import java.util.Set;

import static java.util.Map.entry;

public class SsfTransmitterMetadataWellKnownProvider implements WellKnownProvider {

    private final KeycloakSession session;

    public SsfTransmitterMetadataWellKnownProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getConfig() {

        UriInfo frontendUriInfo = session.getContext().getUri(UrlType.FRONTEND);
        UriInfo backendUriInfo = session.getContext().getUri(UrlType.BACKEND);
        UriInfo adminUriInfo = session.getContext().getUri(UrlType.ADMIN);
        UriBuilder backendUriBuilder = RealmsResource.protocolUrl(backendUriInfo);

        RealmModel realm = session.getContext().getRealm();

        String issuer = Urls.realmIssuer(frontendUriInfo.getBaseUri(), realm.getName());
        URI jwksUri = backendUriBuilder.clone().path(OIDCLoginProtocolService.class, "certs")
                .build(realm.getName(), OIDCLoginProtocol.LOGIN_PROTOCOL);

        // TODO use proper URI builder
        URI configurationUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/streams").build(realm.getName());

        URI statusUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/status").build(realm.getName());

        URI addSubjectUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/subjects:add").build(realm.getName());
        URI removeSubjectUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/subjects:remove").build(realm.getName());

        URI verifyEndpointUri = adminUriInfo.getBaseUriBuilder().path("/admin/realms/{realm}/ssf/verify").build(realm.getName());

        String specVersion = "1_0-ID2";
        Set<String> deliveryMethods = Set.of(DeliveryMethod.PUSH_BASED.getSpecUrn(), DeliveryMethod.POLL_BASED.getSpecUrn());

        var metadata = new SsfTransmitterMetadata();
        metadata.setSpecVersion(specVersion);
        metadata.setIssuer(issuer);
        metadata.setJwksUri(jwksUri.toString());
        metadata.setDeliveryMethodSupported(deliveryMethods);
        metadata.setConfigurationEndpoint(configurationUri.toString());
        metadata.setStatusEndpoint(statusUri.toString());
        metadata.setAddSubjectEndpoint(addSubjectUri.toString());
        metadata.setRemoveSubjectEndpoint(removeSubjectUri.toString());
        metadata.setVerificationEndpoint(verifyEndpointUri.toString());
        // metadata.setCriticalSubjectMembers(Set.of("tenant", "user"));
        metadata.setDefaultSubjects("NONE"); // ALL/NONE
        metadata.setAuthorizationSchemes(List.of( //
                Map.of("spec_urn", AuthorizationScheme.OAUTH.getSpecUrn()), //
                Map.of("spec_urn", AuthorizationScheme.OAUTH_MTLS.getSpecUrn()) //
        ));

        return metadata;
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

        public URI toUri() {
            return URI.create(specUrn);
        }
    }
}
