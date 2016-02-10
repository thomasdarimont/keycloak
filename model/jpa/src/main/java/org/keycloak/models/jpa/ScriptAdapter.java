package org.keycloak.models.jpa;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ScriptModel;
import org.keycloak.models.jpa.entities.ScriptEntity;

import javax.persistence.EntityManager;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptAdapter implements ScriptModel {

    private final KeycloakSession session;

    protected ScriptEntity script;
    protected EntityManager em;
    protected RealmModel realm;

    public ScriptAdapter(KeycloakSession session, RealmModel realm, EntityManager em, ScriptEntity script) {

        this.session = session;
        this.realm = realm;
        this.em = em;
        this.script = script;
    }

    @Override
    public String getId() {
        return script.getId();
    }

    @Override
    public String getName() {
        return script.getName();
    }

    @Override
    public String getType() {
        return script.getType();
    }

    @Override
    public String getCode() {
        return script.getCode();
    }

    @Override
    public String getDescription() {
        return script.getDescription();
    }
}
