package org.keycloak.scim.provisioning.planner;

import java.util.List;
import java.util.stream.Collectors;

import org.keycloak.models.UserModel;
import org.keycloak.scim.provisioning.mapping.SubjectView;

/** A Keycloak user as core's mapping sees it. Reads the model lazily, inside the caller's tx. */
public final class KeycloakSubjectView implements SubjectView {
    private final UserModel user;

    public KeycloakSubjectView(UserModel user) {
        this.user = user;
    }

    @Override
    public String id() {
        return user.getId();
    }

    @Override
    public String username() {
        return user.getUsername();
    }

    @Override
    public String email() {
        return user.getEmail();
    }

    @Override
    public String firstName() {
        return user.getFirstName();
    }

    @Override
    public String lastName() {
        return user.getLastName();
    }

    @Override
    public boolean enabled() {
        return user.isEnabled();
    }

    @Override
    public List<String> attribute(String name) {
        return user.getAttributeStream(name).collect(Collectors.toList());
    }
}
