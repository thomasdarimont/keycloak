package org.keycloak.scripting;

import org.keycloak.models.ScriptModel;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class Script implements ScriptModel {

    private String id;

    private String name;

    private String type;

    private String code;

    private String comment;

    public Script(String id, String name, String type, String code, String comment) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.code = code;
        this.comment = comment;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "Script{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", code='" + code + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
