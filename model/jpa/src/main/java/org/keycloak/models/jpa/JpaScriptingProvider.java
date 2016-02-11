package org.keycloak.models.jpa;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ScriptModel;
import org.keycloak.models.jpa.entities.ScriptEntity;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scripting.AbstractJsr223ScriptingProvider;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class JpaScriptingProvider extends AbstractJsr223ScriptingProvider {

    private final EntityManager em;
    private final KeycloakSession session;

    public JpaScriptingProvider(ScriptEngineManager scriptEngineManager, EntityManager em, KeycloakSession session) {

        super(scriptEngineManager);
        this.em = em;
        this.session = session;
    }

    @Override
    public List<ScriptModel> findAllScripts() {

        RealmModel realm = session.getContext().getRealm();

        TypedQuery<ScriptEntity> query = em.createNamedQuery(ScriptEntity.GET_ALL_SCRIPTS_BY_REALM, ScriptEntity.class);
        query.setParameter("realmId", realm.getId());

        List<ScriptEntity> scripts = query.getResultList();
        if (scripts.isEmpty()){
            return Collections.emptyList();
        }

        List<ScriptModel> scriptAdapters = new ArrayList<>(scripts.size());
        for (ScriptEntity script : scripts){
            scriptAdapters.add(new ScriptAdapter(session, realm, em, script));
        }

        return scriptAdapters;
    }

    @Override
    public ScriptModel findScript(String scriptName) {

        RealmModel realm = session.getContext().getRealm();

        TypedQuery<ScriptEntity> query = em.createNamedQuery(ScriptEntity.GET_SCRIPT_BY_REALM_AND_NAME, ScriptEntity.class);
        query.setParameter("name", scriptName);
        query.setParameter("realmId", realm.getId());

        List<ScriptEntity> entities = query.getResultList();
        if (entities.isEmpty()) {
            return null;
        }

        return new ScriptAdapter(session, realm, em, entities.get(0));
    }

    @Override
    public ScriptModel saveScript(ScriptModel script) {

        String id = script.getId() == null ? KeycloakModelUtils.generateId() : script.getId();
        RealmModel realm = session.getContext().getRealm();

        ScriptEntity entity = new ScriptEntity();
        entity.setId(id);
        entity.setRealmId(realm.getId());
        entity.setName(script.getName());
        entity.setCode(script.getCode());
        entity.setDescription(script.getDescription());
        entity.setType(script.getType());

        em.persist(entity);
        em.flush();

        return new ScriptAdapter(session, realm, em, entity);

    }

    @Override
    public void close() {
        //NOOP
    }
}
