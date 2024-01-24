/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class DefaultPasswordPolicyManagerProvider implements PasswordPolicyManagerProvider {

    private final KeycloakSession session;

    public DefaultPasswordPolicyManagerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public PolicyError validate(RealmModel realm, UserModel user, String password) {
        return validate(password, new PasswordPolicyContext().setRealm(realm).setUser(user));
    }

    @Override
    public PolicyError validate(String user, String password) {
        return validate(password, new PasswordPolicyContext().setRealm(session.getContext().getRealm()).setUsername(user));
    }

    @Override
    public PolicyError validate(String password, PasswordPolicyContext policyContext) {
        for (PasswordPolicyProvider p : getProviders(session, policyContext)) {
            PolicyError policyError = p.validate(password, policyContext);
            if (policyError != null) {
                return policyError;
            }
        }
        return null;
    }

    @Override
    public void close() {
    }

    protected List<PasswordPolicyProvider> getProviders(KeycloakSession session, PasswordPolicyContext policyContext) {
        LinkedList<PasswordPolicyProvider> list = new LinkedList<>();
        PasswordPolicy policy = policyContext.getPasswordPolicy();
        for (String id : policy.getPolicies()) {
            PasswordPolicyProvider provider = session.getProvider(PasswordPolicyProvider.class, id);
            list.add(provider);
        }
        return list;
    }

}
