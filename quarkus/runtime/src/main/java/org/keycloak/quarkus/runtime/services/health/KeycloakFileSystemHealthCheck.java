package org.keycloak.quarkus.runtime.services.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;

@Liveness
@ApplicationScoped
public class KeycloakFileSystemHealthCheck implements HealthCheck {

    private static final File KC_HOME_DIR = new File(".");
    public static final long MIN_FREE_SPACE = 0L;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Filesystem health check").up();

        long freeBytes = KC_HOME_DIR.getFreeSpace();
        builder.withData("freeBytes", freeBytes);
        if (freeBytes <= MIN_FREE_SPACE) {
            builder.down();
        }

        return builder.build();
    }
}
