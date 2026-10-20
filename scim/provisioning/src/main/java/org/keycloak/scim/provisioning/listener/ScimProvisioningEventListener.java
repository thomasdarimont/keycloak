package org.keycloak.scim.provisioning.listener;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.scim.provisioning.planner.Subjects;

/**
 * Turns user and admin events into markers. The first statement is a type switch, so the many
 * events this global listener sees and ignores (logins, token refreshes) cost nothing.
 * Events are hints: the listener records only which subject changed.
 */
public class ScimProvisioningEventListener implements EventListenerProvider {
    private final KeycloakSession session;

    public ScimProvisioningEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == null) return;
        switch (event.getType()) {
            case REGISTER:
            case UPDATE_PROFILE:
            case UPDATE_EMAIL:
            case VERIFY_EMAIL:
            case DELETE_ACCOUNT:
            case USER_DISABLED_BY_PERMANENT_LOCKOUT:
            case IDENTITY_PROVIDER_FIRST_LOGIN:
                if (event.getUserId() != null) {
                    Enqueuer.fromEvent(session, event.getRealmId(), Subjects.user(event.getUserId()), false);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // the string form: custom resource types (our own replay events, other extensions') are fine
        String type = event.getResourceTypeAsString();
        if (type == null || event.getResourcePath() == null) return;
        String[] p = event.getResourcePath().split("/");
        switch (type) {
            case "USER":
                if (p.length >= 2 && p[0].equals("users")) {
                    Enqueuer.fromEvent(session, event.getRealmId(), Subjects.user(p[1]), false);
                }
                break;
            case "GROUP_MEMBERSHIP":
                // users/{userId}/groups/{groupId}; the model events cover this too, the marks coalesce
                if (p.length >= 4 && p[0].equals("users")) {
                    Enqueuer.fromEvent(session, event.getRealmId(), Subjects.user(p[1]), false);
                    Enqueuer.fromEvent(session, event.getRealmId(), Subjects.group(p[3]), false);
                }
                break;
            case "GROUP":
                if (p.length >= 2 && p[0].equals("groups")) {
                    Enqueuer.fromEvent(session, event.getRealmId(), Subjects.group(p[1]), false);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void close() {}
}
