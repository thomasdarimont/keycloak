package org.keycloak.models;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public interface ScriptModel {

    String getId();

    String getName();

    String getType();

    String getCode();

    String getDescription();
}
