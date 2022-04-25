package org.keycloak.quarkus.runtime.services.health.realm;

import io.smallrye.health.checks.InetAddressHealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.keycloak.health.AbstractHealthIndicator;
import org.keycloak.health.HealthState;
import org.keycloak.health.Health;
import org.keycloak.health.IntermediateHealth;
import org.keycloak.models.RealmModel;

import java.util.Map;

public class EmailHealthIndicator extends AbstractHealthIndicator {

    private final int timeoutInMillis;

    public EmailHealthIndicator(String name, int timeoutInMillis) {
        super(name);
        this.timeoutInMillis = timeoutInMillis;
    }

    @Override
    public boolean isApplicable(RealmModel realm) {
        // TODO check if email health checks are enabled for this realm
        return !realm.getSmtpConfig().isEmpty();
    }

    @Override
    public Health check(RealmModel realm) {

        Map<String, String> smtpConfig = realm.getSmtpConfig();
        String hostname = smtpConfig.get("host");

        // Adapt / reuse the existing health check logic from smallrye-health
        HealthCheckResponse response = new InetAddressHealthCheck(hostname) //
                .timeout(timeoutInMillis) //
                .name(getName()) //
                .call();

        IntermediateHealth status = new IntermediateHealth(response.getName())
                .withData(response.getData().get());
        switch (response.getStatus()) {
            case UP:
                return status.withState(HealthState.UP);
            case DOWN:
            default:
                return status.withState(HealthState.DOWN);
        }
    }
}
