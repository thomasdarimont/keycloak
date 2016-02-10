package org.keycloak.models.jpa.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import static org.keycloak.models.jpa.entities.ScriptEntity.GET_ALL_SCRIPTS_BY_REALM;
import static org.keycloak.models.jpa.entities.ScriptEntity.GET_SCRIPT_BY_REALM_AND_NAME;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
@NamedQueries({
        @NamedQuery(name=GET_ALL_SCRIPTS_BY_REALM, query="select s from ScriptEntity s where s.realmId = :realmId order by s.name"),
        @NamedQuery(name=GET_SCRIPT_BY_REALM_AND_NAME, query="select s from ScriptEntity s where s.realmId = :realmId and s.name = :name")
})
@Entity
@Table(name="SCRIPT_ENTITY", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "REALM_ID", "SCRIPT_NAME" }),
})
public class ScriptEntity {

    public static final String GET_ALL_SCRIPTS_BY_REALM = "getAllScriptsByRealm";

    public static final String GET_SCRIPT_BY_REALM_AND_NAME ="getScriptByRealmAndName";

    @Id
    @Column(name="ID", length = 36)
    private String id;

    @Column(name = "REALM_ID")
    private String realmId;

    @Column(name = "SCRIPT_NAME")
    private String name;

    @Column(name = "SCRIPT_TYPE")
    private String type;

    @Column(name = "SCRIPT_CODE", length = 2048)
    private String code;

    @Column(name = "SCRIPT_DESCRIPTION", length = 2048)
    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}