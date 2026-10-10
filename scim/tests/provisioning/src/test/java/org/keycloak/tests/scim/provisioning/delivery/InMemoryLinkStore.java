package org.keycloak.tests.scim.provisioning.delivery;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.delivery.LinkStore;

/** A {@link LinkStore} in a map, optionally failing every save (a crash after the HTTP call). */
final class InMemoryLinkStore implements LinkStore {

    final Map<String, LinkRecord> links = new ConcurrentHashMap<>();

    volatile boolean failSaves;

    @Override
    public Optional<LinkRecord> find(String subject) {
        return Optional.ofNullable(links.get(subject));
    }

    @Override
    public void save(LinkRecord link) {
        if (failSaves) {
            throw new IllegalStateException("simulated crash before the link was written");
        }
        links.put(link.subject(), link);
    }
}
