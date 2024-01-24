/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

import org.keycloak.models.KeycloakContext;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 *
 * @author rmartinc
 */
public class MaximumLengthPasswordPolicyProvider implements PasswordPolicyProvider {

    private static final String ERROR_MESSAGE = "invalidPasswordMaxLengthMessage";

    private final KeycloakContext context;

    public MaximumLengthPasswordPolicyProvider(KeycloakContext context) {
        this.context = context;
    }

    @Override
    public PolicyError validate(String username, String password) {
        return validate(password, new PasswordPolicyContext().setRealm(context.getRealm()));
    }

    @Override
    public PolicyError validate(RealmModel realm, UserModel user, String password) {
        return validate(password, new PasswordPolicyContext().setRealm(realm));
    }

    @Override
    public PolicyError validate(String password, PasswordPolicyContext policyContext) {
        int max = policyContext.getPasswordPolicy().getPolicyConfig(MaximumLengthPasswordPolicyProviderFactory.ID);
        return password.length() > max ? new PolicyError(ERROR_MESSAGE, max) : null;
    }

    @Override
    public Object parseConfig(String value) {
        return parseInteger(value, MaximumLengthPasswordPolicyProviderFactory.DEFAULT_MAX_LENGTH);
    }

    @Override
    public void close() {
    }
}
