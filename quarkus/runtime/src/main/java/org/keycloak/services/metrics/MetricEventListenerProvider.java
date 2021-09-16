package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.keycloak.events.EventType.CLIENT_LOGIN;
import static org.keycloak.events.EventType.CLIENT_LOGIN_ERROR;
import static org.keycloak.events.EventType.CODE_TO_TOKEN;
import static org.keycloak.events.EventType.CODE_TO_TOKEN_ERROR;
import static org.keycloak.events.EventType.LOGIN;
import static org.keycloak.events.EventType.LOGIN_ERROR;
import static org.keycloak.events.EventType.LOGOUT;
import static org.keycloak.events.EventType.LOGOUT_ERROR;
import static org.keycloak.events.EventType.REFRESH_TOKEN;
import static org.keycloak.events.EventType.REFRESH_TOKEN_ERROR;
import static org.keycloak.events.EventType.REGISTER;
import static org.keycloak.events.EventType.REGISTER_ERROR;
import static org.keycloak.services.metrics.Metrics.tag;

public class MetricEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(MetricEventListenerProvider.class);

    private static final String USER_EVENT_PREFIX = "keycloak_user_event_";

    private static final String ADMIN_EVENT_PREFIX = "keycloak_admin_event_";

    private final Map<String, Metadata> genericCounters;

    protected final MetricRegistry metricRegistry;

    protected final Map<EventType, Consumer<Event>> customUserEventHandlers;

    public MetricEventListenerProvider() {
        this.metricRegistry = Metrics.getMetricRegistry();
        this.customUserEventHandlers = registerCustomUserEventHandlers();
        this.genericCounters = registerGenericEventCounters();
    }

    private Map<EventType, Consumer<Event>> registerCustomUserEventHandlers() {
        Map<EventType, Consumer<Event>> map = new HashMap<>();
        map.put(LOGIN, this::recordUserLogin);
        map.put(LOGIN_ERROR, this::recordUserLoginError);
        map.put(LOGOUT, this::recordUserLogout);
        map.put(LOGOUT_ERROR, this::recordUserLogoutError);
        map.put(CLIENT_LOGIN, this::recordClientLogin);
        map.put(CLIENT_LOGIN_ERROR, this::recordClientLoginError);
        map.put(REGISTER, this::recordUserRegistration);
        map.put(REGISTER_ERROR, this::recordUserRegistrationError);
        map.put(REFRESH_TOKEN, this::recordOauthTokenRefresh);
        map.put(REFRESH_TOKEN_ERROR, this::recordOauthTokenRefreshError);
        map.put(CODE_TO_TOKEN, this::recordOauthCodeToToken);
        map.put(CODE_TO_TOKEN_ERROR, this::recordOauthCodeToTokenError);
        return map;
    }

    private Map<String, Metadata> registerGenericEventCounters() {
        Map<String, Metadata> initCounters = new HashMap<>();
        registerUserEventCounters(initCounters);
        registerAdminEventCounters(initCounters);
        return Collections.unmodifiableMap(initCounters);
    }

    /**
     * Counters for all user events
     */
    private void registerUserEventCounters(Map<String, Metadata> counters) {

        for (EventType type : EventType.values()) {
            if (customUserEventHandlers.containsKey(type)) {
                continue;
            }

            // TODO add capability to ignore certain user events

            String counterName = buildCounterName(type);
            Metadata counter = createCounter(counterName, false);
            counters.put(counterName, counter);
        }
    }

    /**
     * Counters for all admin events
     */
    private void registerAdminEventCounters(Map<String, Metadata> counters) {

        for (OperationType type : OperationType.values()) {
            String counterName = buildCounterName(type);
            Metadata counter = createCounter(counterName, true);
            counters.put(counterName, counter);
        }
    }

    @Override
    public void onEvent(Event event) {
        Consumer<Event> handler = customUserEventHandlers.getOrDefault(event.getType(), this::recordGenericUserEvent);
        handler.accept(event);
    }

    private void recordUserLogout(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("provider", provider),
//                tag("client_id", event.getClientId()),
        };
        metricRegistry.counter(Metrics.USER_LOGOUT_SUCCESS_TOTAL, tags).inc();
    }

    private void recordUserLogoutError(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("provider", provider),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
        };
        metricRegistry.counter(Metrics.USER_LOGOUT_ERROR_TOTAL, tags).inc();
    }

    protected void recordOauthCodeToTokenError(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
                tag("provider", provider),
        };
        metricRegistry.counter(Metrics.OAUTH_CODE_TO_TOKEN_ERROR_TOTAL, tags).inc();
    }

    protected void recordOauthCodeToToken(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("provider", provider),
        };
        metricRegistry.counter(Metrics.OAUTH_CODE_TO_TOKEN_SUCCESS_TOTAL, tags).inc();
    }

    protected void recordClientLogin(Event event) {
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
        };
        metricRegistry.counter(Metrics.CLIENT_LOGIN_SUCCESS_TOTAL, tags).inc();
    }

    protected void recordClientLoginError(Event event) {
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
        };
        metricRegistry.counter(Metrics.CLIENT_LOGIN_ERROR_TOTAL, tags).inc();
    }

    protected void recordOauthTokenRefreshError(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
                tag("provider", provider),
        };
        metricRegistry.counter(Metrics.OAUTH_TOKEN_REFRESH_ERROR_TOTAL, tags).inc();
    }

    protected void recordOauthTokenRefresh(Event event) {
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
        };
        metricRegistry.counter(Metrics.OAUTH_TOKEN_REFRESH_SUCCESS_TOTAL, tags).inc();
    }

    protected void recordUserRegistrationError(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
                tag("provider", provider),
        };
        metricRegistry.counter(Metrics.USER_REGISTER_ERROR_TOTAL, tags).inc();
    }

    protected void recordUserRegistration(Event event) {
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
        };
        metricRegistry.counter(Metrics.USER_REGISTER_SUCCESS_TOTAL, tags).inc();
    }

    protected void recordUserLoginError(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("client_id", event.getClientId()),
                tag("error", event.getError()),
                tag("provider", provider),
        };
        metricRegistry.counter(Metrics.USER_LOGIN_ERROR_TOTAL, tags).inc();
    }

    protected void recordUserLogin(Event event) {
        String provider = getIdentityProvider(event);
        Tag[] tags = new Tag[]{
                tag("realm", event.getRealmId()),
                tag("provider", provider),
                tag("client_id", event.getClientId()),
        };
        metricRegistry.counter(Metrics.USER_LOGIN_SUCCESS_TOTAL, tags).inc();
    }

    /**
     * Count generic user event
     *
     * @param event User event
     */
    protected void recordGenericUserEvent(Event event) {

        EventType eventType = event.getType();
        String counterName = buildCounterName(eventType);
        Metadata counterMetadata = genericCounters.get(counterName);
        String realmName = event.getRealmId();

        if (counterMetadata == null) {
            log.warnf("Counter %s for event type %s does not exist. Realm: %s", counterName, eventType.name(), realmName);
            return;
        }

        Tag[] tags = new Tag[]{
                tag("realm", realmName),
        };


        metricRegistry.counter(counterMetadata, tags).inc();
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {

        // TODO add capability to ignore certain admin events

        OperationType operationType = event.getOperationType();
        String counterName = buildCounterName(operationType);
        Metadata counterMetadata = genericCounters.get(counterName);
        ResourceType resourceType = event.getResourceType();
        String realmName = event.getRealmId();

        if (counterMetadata == null) {
            log.warnf("Counter %s for admin event operation type %s does not exist. Resource type: %s, realm: %s", counterName, operationType.name(), resourceType.name(), realmName);
            return;
        }

        Tag[] tags = {
                tag("realm", realmName),
                tag("resource", resourceType.name())
        };

        metricRegistry.counter(counterMetadata, tags).inc();
    }

    @Override
    public void close() {
        // NOOP
    }

    /**
     * Retrieve the identity provider name from event details or
     * <p>
     * default to {@value "keycloak"}.
     *
     * @param event User event
     * @return Identity provider name
     */
    private String getIdentityProvider(Event event) {

        String identityProvider = null;
        if (event.getDetails() != null) {
            identityProvider = event.getDetails().get("identity_provider");
        }

        if (identityProvider == null) {
            identityProvider = "keycloak";
        }

        return identityProvider;
    }

    private String buildCounterName(OperationType type) {
        return ADMIN_EVENT_PREFIX + type.name();
    }

    private String buildCounterName(EventType type) {
        return USER_EVENT_PREFIX + type.name();
    }

    /**
     * Creates a counter based on a event name
     */
    private Metadata createCounter(String name, boolean isAdmin) {
        String description = isAdmin ? "Generic KeyCloak Admin event" : "Generic KeyCloak User event";
        return Metadata.builder().withName(name).withDescription(description).withType(MetricType.COUNTER).build();
    }

}
