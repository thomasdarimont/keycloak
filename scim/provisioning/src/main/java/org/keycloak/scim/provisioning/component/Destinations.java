package org.keycloak.scim.provisioning.component;

import java.util.List;
import java.util.stream.Collectors;

import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProvider;

/**
 * The destinations of a realm: its user-storage components whose provider id is
 * {@code scim-provisioning}. Served from Keycloak's realm cache.
 */
public final class Destinations {
    private Destinations() {}

    public static List<Destination> of(RealmModel realm) {
        return realm
                .getComponentsStream(realm.getId(), UserStorageProvider.class.getName())
                .filter(c -> DestinationProviderFactory.ID.equals(c.getProviderId()))
                .map(c -> Destination.of(c, realm.getId()))
                .collect(Collectors.toList());
    }

    /** The destination with this component id in this realm, or null. */
    public static Destination find(RealmModel realm, String destinationId) {
        var c = realm.getComponent(destinationId);
        if (c == null || !DestinationProviderFactory.ID.equals(c.getProviderId())) return null;
        return Destination.of(c, realm.getId());
    }
}
