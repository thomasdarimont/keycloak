package org.keycloak.models.metrics;

public class RealmReference {
    private final String id;

    private final String name;

    public RealmReference(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
