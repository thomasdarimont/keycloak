package org.keycloak.storage.adapter;

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import java.util.List;

public class FakeUserUtil {

    public static UserModel createFakeUser(KeycloakSession session, RealmModel realm, String username) {
        UserModel fakeUser = new InMemoryUserAdapter(session, realm, "fakeId"){
            @Override
            public SubjectCredentialManager credentialManager() {
                return new UserCredentialManager(session, realm, this) {
                    @Override
                    public boolean isConfiguredFor(String type) {
                        return true;
                    }

                    @Override
                    public boolean isValid(List<CredentialInput> inputs) {
                        return false;
                    }

                    @Override
                    public boolean isValid(CredentialInput... inputs) {
                        return false;
                    }
                };
            }
        };
        fakeUser.setUsername(username);
        fakeUser.setEnabled(true);

        return fakeUser;
    }
}
