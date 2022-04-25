package org.keycloak.services.resources;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.common.ClientConnection;
import org.keycloak.health.CompositeHealth;
import org.keycloak.health.HealthIndicator;
import org.keycloak.health.HealthState;
import org.keycloak.health.Health;
import org.keycloak.health.IntermediateHealth;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class HealthService {

    private static final Response NOT_FOUND = Response.status(Response.Status.NOT_FOUND).build();

    private static final Comparator<HealthIndicator> HEALTH_INDICATOR_COMPARATOR = //
            Comparator.comparing(HealthIndicator::getName, Comparator.naturalOrder());

    private final RealmModel realm;

    @Context
    private KeycloakSession session;

    @Context
    private ClientConnection clientConnection;

    @Context
    private HttpRequest request;

    public HealthService(RealmModel realm) {
        this.realm = Objects.requireNonNull(realm);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response healthCheck() {

        Set<HealthIndicator> healthIndicators = collectHealthIndicators();
        if (healthIndicators.isEmpty()) {
            return NOT_FOUND;
        }

        CompositeHealth compositeHealth = aggregateHealthIndicators(healthIndicators);

        return toHealthResponse(compositeHealth);
    }

    protected Set<HealthIndicator> collectHealthIndicators() {
        // TODO add support for sorting health checks explicitly
        Set<HealthIndicator> checks = new TreeSet<>(HEALTH_INDICATOR_COMPARATOR);
        checks.addAll(this.session.getAllProviders(HealthIndicator.class));
        return checks;
    }

    protected CompositeHealth aggregateHealthIndicators(Set<HealthIndicator> healthIndicators) {

        CompositeHealth compositeHealth = new CompositeHealth(realm.getName());

        for (HealthIndicator healthIndicator : healthIndicators) {
            // only show relevant health indicators
            if (!healthIndicator.isApplicable(realm)) {
                continue;
            }

            // execute the health check in a fail-safe way
            try {
                Health health = healthIndicator.check(realm);
                // Support health status composition.
                if (health instanceof CompositeHealth) {
                    compositeHealth.addAll((CompositeHealth) health);
                } else {
                    compositeHealth.add(health);
                }
            } catch (Exception ex) {
                compositeHealth.add(new IntermediateHealth(healthIndicator.getName()) //
                        .withState(HealthState.DOWN) //
                        .withData("error", "health-check-failed") //
                        .withData("errorMessage", ex.getMessage()));
            }
        }
        return compositeHealth;
    }

    protected Response toHealthResponse(CompositeHealth healthStatus) {

        KeycloakHealthResponse response = new KeycloakHealthResponse(healthStatus);

        switch (response.getState()) {
            case UP:
                return Response.ok(response).build();
            case DOWN:
            default:
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(response).build();
        }
    }

    /**
     * Top Level Realm Level Health response
     */
    static class KeycloakHealthResponse {

        private final String name;

        private final HealthState state;

        private final List<Health> checks;

        public KeycloakHealthResponse(String name, HealthState state, List<Health> checks) {
            this.name = name;
            this.state = state;
            this.checks = checks;
        }

        public KeycloakHealthResponse(CompositeHealth healthStatus) {
            this(healthStatus.getName(), healthStatus.getState(), healthStatus.getChecks());
        }

        public String getName() {
            return name;
        }

        public HealthState getState() {
            return state;
        }

        public List<Health> getChecks() {
            return checks;
        }
    }
}
