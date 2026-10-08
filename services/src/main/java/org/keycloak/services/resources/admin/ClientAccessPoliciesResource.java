package org.keycloak.services.resources.admin;

import java.util.stream.Stream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.keycloak.authentication.clientaccess.ClientAccessPolicy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

/**
 * Read-only view on the realm's client access policies for admins that may manage clients but not realm components.
 */
@Extension(name = KeycloakOpenAPI.Profiles.ADMIN, value = "")
public class ClientAccessPoliciesResource {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;

    public ClientAccessPoliciesResource(KeycloakSession session, AdminPermissionEvaluator auth) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.auth = auth;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENTS)
    @Operation(summary = "List the client access policies of the realm that a client can reference")
    public Stream<ComponentRepresentation> getPolicies() {
        auth.clients().requireList();
        return realm.getComponentsStream(realm.getId(), ClientAccessPolicy.class.getName())
                .map(model -> ModelToRepresentation.toRepresentation(session, model, false));
    }
}
