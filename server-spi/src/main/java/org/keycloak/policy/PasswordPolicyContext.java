/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.policy;

import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Holds context information for password validation.
 */
public class PasswordPolicyContext {

    private String username;

    private RealmModel realm;

    private UserModel user;

    private PasswordPolicy passwordPolicy;

    /**
     * Returns the given username if set, otherwise to the username of the given {@link UserModel} is returned.
     * @return
     */
    public String getUsername() {

        if (username != null) {
            return username;
        }

        if (user != null) {
            return user.getUsername();
        }

        return null;
    }

    public PasswordPolicyContext setUsername(String username) {
        this.username = username;
        return this;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public PasswordPolicyContext setRealm(RealmModel realm) {
        this.realm = realm;
        return this;
    }

    public UserModel getUser() {
        return user;
    }

    public PasswordPolicyContext setUser(UserModel user) {
        this.user = user;
        return this;
    }

    /**
     * Returns the {@link PasswordPolicy} if set, falls back to the password policy configured in the {@link RealmModel}.
     * @return
     */
    public PasswordPolicy getPasswordPolicy() {
        if (passwordPolicy != null) {
            return passwordPolicy;
        }
        if (realm != null) {
            return realm.getPasswordPolicy();
        }
        return null;
    }

    public PasswordPolicyContext setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
        return this;
    }
}
