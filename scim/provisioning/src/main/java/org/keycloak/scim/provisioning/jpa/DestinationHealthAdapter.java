package org.keycloak.scim.provisioning.jpa;

import org.keycloak.models.jpa.JpaModel;
import org.keycloak.models.jpa.entities.ScimProvisioningHealthEntity;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;

/** {@link DestinationHealthModel} over {@link ScimProvisioningHealthEntity}. */
public class DestinationHealthAdapter implements DestinationHealthModel, JpaModel<ScimProvisioningHealthEntity> {

    protected final ScimProvisioningHealthEntity health;

    public DestinationHealthAdapter(ScimProvisioningHealthEntity health) {
        this.health = health;
    }

    @Override
    public ScimProvisioningHealthEntity getEntity() {
        return health;
    }

    @Override
    public String getRealmId() {
        return health.getRealmId();
    }

    @Override
    public String getDestinationId() {
        return health.getDestinationId();
    }

    @Override
    public long getCreatedAt() {
        return health.getCreatedAt();
    }

    @Override
    public Long getLastSuccessAt() {
        return health.getLastSuccessAt();
    }

    @Override
    public String getLastErrorClass() {
        return health.getLastErrorClass();
    }

    @Override
    public String getLastError() {
        return health.getLastError();
    }

    @Override
    public Long getFailingSince() {
        return health.getFailingSince();
    }

    @Override
    public Long getRetryAt() {
        return health.getRetryAt();
    }

    @Override
    public int getConsecutiveFailures() {
        return health.getConsecutiveFailures();
    }

    @Override
    public Long getLastReconcileAt() {
        return health.getLastReconcileAt();
    }

    @Override
    public String getLastReconcileStatus() {
        return health.getLastReconcileStatus();
    }

    @Override
    public String getLastReconcileReport() {
        return health.getLastReconcileReport();
    }

    @Override
    public int getMismatches() {
        return health.getMismatches();
    }
}
