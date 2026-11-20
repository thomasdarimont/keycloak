package org.keycloak.events;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the built-in event types and the custom event types provided through {@link EventTypeProviderFactory}.
 * <p>
 * The custom event types are registered by the Keycloak session factory once the provider factories are loaded, and
 * before {@link org.keycloak.provider.ProviderFactory#postInit} is called on any of them. Until then only the
 * built-in event types are known.
 */
public final class EventTypeRegistry {

    private static final Map<String, EventType> BUILTIN = builtinTypes();

    private static volatile Map<String, EventType> types = BUILTIN;

    private EventTypeRegistry() {
    }

    /**
     * Replaces the registered custom event types with the event types of the given factories.
     *
     * @throws IllegalStateException if an event type name is used more than once
     */
    public static void register(Collection<? extends EventTypeProviderFactory> factories) {
        Map<String, EventType> all = new LinkedHashMap<>(BUILTIN);
        Map<String, String> owners = new LinkedHashMap<>();

        factories.stream().sorted(Comparator.comparing(EventTypeProviderFactory::getId)).forEach(factory -> {
            for (EventType type : factory.getEventTypes()) {
                EventType existing = all.putIfAbsent(type.name(), type);
                if (existing != null && existing != type) {
                    throw new IllegalStateException("Duplicate event type '" + type.name() + "' provided by '" + factory.getId()
                            + "', already defined by " + owners.getOrDefault(type.name(), "Keycloak"));
                }
                owners.put(type.name(), "'" + factory.getId() + "'");
            }
        });

        types = Collections.unmodifiableMap(all);
    }

    static EventType valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("Name is null");
        }
        EventType type = types.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown event type: " + name);
        }
        return type;
    }

    static EventType[] values() {
        return types.values().toArray(new EventType[0]);
    }

    private static Map<String, EventType> builtinTypes() {
        Map<String, EventType> builtin = new LinkedHashMap<>();
        for (BuiltinEventType type : BuiltinEventType.values()) {
            builtin.put(type.name(), type);
        }
        return Collections.unmodifiableMap(builtin);
    }
}
