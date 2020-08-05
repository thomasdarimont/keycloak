/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.validation;

import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.Map;

/**
 * Denotes a context in which the Validation takes place. A {@link ValidationContext} should be created for a
 * batch of validation checks for a given entity.
 */
public class ValidationContext {

    private final RealmModel realm;

    // user registration, user profile update, client registration, realm creation
    private final ValidationContextKey contextKey;

    // additional context specific attributes
    private final Map<String, Object> attributes;

    /**
     * Holds the current {@link UserModel}
     */
    private final UserModel user;

    /**
     * Holds the current {@link ClientModel}
     */
    private final ClientModel client;

    public ValidationContext(ValidationContext that) {
        this(that.getRealm(), that.getContextKey(), that.getAttributes(), that.getUser(), that.getClient());
    }

    public ValidationContext(RealmModel realm, ValidationContextKey contextKey) {
        this(realm, contextKey, Collections.emptyMap(), null, null);
    }

    public ValidationContext(RealmModel realm, ValidationContextKey contextKey, Map<String, Object> attributes) {
        this(realm, contextKey, attributes, null, null);
    }

    public ValidationContext(RealmModel realm, ValidationContextKey contextKey, Map<String, Object> attributes, UserModel user, ClientModel client) {
        this.realm = realm;
        this.contextKey = contextKey;
        this.attributes = attributes;
        this.user = user;
        this.client = client;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public ValidationContextKey getContextKey() {
        return contextKey;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public boolean getAttributeAsBoolean(String name) {
        return attributes.get(name) == Boolean.TRUE;
    }

    public String getAttributeAsString(String name) {
        Object value = attributes.get(name);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    public UserModel getUser() {
        return user;
    }

    public ClientModel getClient() {
        return client;
    }
}
