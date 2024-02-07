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

package org.keycloak.authentication.authenticators.browser;

import java.util.List;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.LoginFormsUtil;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.UserModelDelegate;
import org.keycloak.services.messages.Messages;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.storage.adapter.FakeUserUtil;
import org.keycloak.storage.adapter.InMemoryUserAdapter;

public class UsernameForm extends UsernamePasswordForm {

    public static final String IGNORE_USER_NOT_FOUND_KEY = "ignoreNotFound";

    public static final boolean IGNORE_USER_NOT_FOUND_DEFAULT = false;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (context.getUser() != null) {
            // We can skip the form when user is re-authenticating. Unless current user has some IDP set, so he can re-authenticate with that IDP
            List<IdentityProviderModel> identityProviders = LoginFormsUtil
                    .filterIdentityProviders(context.getRealm().getIdentityProvidersStream(), context.getSession(), context);
            if (identityProviders.isEmpty()) {
                context.success();
                return;
            }
        }
        super.authenticate(context);
    }

    public boolean validateUser(AuthenticationFlowContext context, MultivaluedMap<String, String> inputData) {
        UserModel user = getUser(context, inputData);

        if (user == null && isIgnoreUserNotFoundEnabled(context)) {
            // pass unknown user to the next stage and let it fail there.
            String attemptedUsername = context.getAuthenticationSession().getAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME);
            user = FakeUserUtil.createFakeUser(context.getSession(), context.getRealm(), attemptedUsername);
        }

        return user != null && super.validateUser(context, user, inputData);
    }

    @Override
    public void testInvalidUser(AuthenticationFlowContext context, UserModel user) {
        super.testInvalidUser(context, user, !isIgnoreUserNotFoundEnabled(context));
    }

    protected boolean isIgnoreUserNotFoundEnabled(AuthenticationFlowContext context) {

        AuthenticatorConfigModel authConfig = context.getAuthenticatorConfig();
        if (authConfig == null) {
            return UsernameForm.IGNORE_USER_NOT_FOUND_DEFAULT;
        }

        if (authConfig.getConfig() == null) {
            return UsernameForm.IGNORE_USER_NOT_FOUND_DEFAULT;
        }

        return Boolean.parseBoolean(authConfig.getConfig().get(IGNORE_USER_NOT_FOUND_KEY));
    }

    @Override
    protected boolean validateForm(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        return validateUser(context, formData);
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        LoginFormsProvider forms = context.form();

        if (!formData.isEmpty()) forms.setFormData(formData);

        return forms.createLoginUsername();
    }

    @Override
    protected Response createLoginForm(LoginFormsProvider form) {
        return form.createLoginUsername();
    }

    @Override
    protected String getDefaultChallengeMessage(AuthenticationFlowContext context) {
        if (context.getRealm().isLoginWithEmailAllowed())
            return Messages.INVALID_USERNAME_OR_EMAIL;
        return Messages.INVALID_USERNAME;
    }
}
