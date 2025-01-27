package org.keycloak.ssf.caep.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.keycloak.ssf.SecurityEvent;

/**
 * The Assurance Level Change event signals that there has been a change in authentication method since the initial user login. This change can be from a weak authentication method to a strong authentication method, or vice versa.
 */
public class AssuranceLevelChange extends SecurityEvent {

    /**
     * The namespace of the values in the current_level and previous_level claims.
     */
    @JsonProperty("namespace")
    protected String namespace;

    /**
     * The current assurance level, as defined in the specified namespace
     */
    @JsonProperty("current_level")
    protected String currentLevel;

    /**
     * The previous assurance level, as defined in the specified namespace If the Transmitter omits this value, the Receiver MUST assume that the previous assurance level is unknown to the Transmitter
     */
    @JsonProperty("previous_level")
    protected String previousLevel;

    /**
     * The assurance level increased or decreased If the Transmitter has specified the previous_level, then the Transmitter SHOULD provide a value for this claim. If present, this MUST be one of the following strings:
     * increase, decrease.
     */
    @JsonProperty("change_direction")
    protected ChangeDirection changeDirection;

    public AssuranceLevelChange() {
        super(CaepEventType.ASSURANCE_LEVEL_CHANGE);
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public void setCurrentLevel(String currentLevel) {
        this.currentLevel = currentLevel;
    }

    @Override
    public String getPreviousLevel() {
        return previousLevel;
    }

    @Override
    public void setPreviousLevel(String previousLevel) {
        this.previousLevel = previousLevel;
    }

    public ChangeDirection getChangeDirection() {
        return changeDirection;
    }

    public void setChangeDirection(ChangeDirection changeDirection) {
        this.changeDirection = changeDirection;
    }

    public enum ChangeDirection {

        INCREASE("increase"),
        DECREASE("decrease");

        private final String type;

        ChangeDirection(String type) {
            this.type = type;
        }

        @JsonValue
        public String getType() {
            return type;
        }
    }
}
