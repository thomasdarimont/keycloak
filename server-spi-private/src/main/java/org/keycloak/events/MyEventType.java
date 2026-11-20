package org.keycloak.events;

import java.util.EnumSet;
import java.util.Set;

/**
 * Sample custom event type for the PoC, an extension would ship this in its own jar.
 */
public enum MyEventType implements EventType {

    MY_EVENT(true),
    MY_EVENT_ERROR(true);

    private final boolean saveByDefault;

    MyEventType(boolean saveByDefault) {
        this.saveByDefault = saveByDefault;
    }

    @Override
    public boolean isSaveByDefault() {
        return saveByDefault;
    }

    public static class Factory implements EventTypeProviderFactory {

        @Override
        public String getId() {
            return "my-events";
        }

        @Override
        public Set<? extends EventType> getEventTypes() {
            return EnumSet.allOf(MyEventType.class);
        }
    }
}
