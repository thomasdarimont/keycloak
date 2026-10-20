package org.keycloak.scim.provisioning.jpa;

import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.entities.ScimProvisioningLinkEntity;
import org.keycloak.scim.provisioning.model.ScimLinkModel;

/** {@link ScimLinkModel} over {@link ScimProvisioningLinkEntity}. */
public class LinkAdapter implements ScimLinkModel, JpaModel<ScimProvisioningLinkEntity> {

    protected final ScimProvisioningLinkEntity link;

    public LinkAdapter(ScimProvisioningLinkEntity link) {
        this.link = link;
    }

    @Override
    public ScimProvisioningLinkEntity getEntity() {
        return link;
    }

    @Override
    public String getRealmId() {
        return link.getRealmId();
    }

    @Override
    public String getDestinationId() {
        return link.getDestinationId();
    }

    @Override
    public String getSubject() {
        return link.getSubject();
    }

    @Override
    public String getRemoteId() {
        return link.getRemoteId();
    }

    @Override
    public String getPayloadHash() {
        return link.getPayloadHash();
    }

    @Override
    public boolean isActive() {
        return link.isActive();
    }

    @Override
    public boolean isDeprovisioned() {
        return link.isDeprovisioned();
    }

    @Override
    public String getUserName() {
        return link.getUserName();
    }

    @Override
    public long getSyncedAt() {
        return link.getSyncedAt();
    }
}
