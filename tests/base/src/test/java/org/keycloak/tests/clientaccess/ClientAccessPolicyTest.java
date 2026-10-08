package org.keycloak.tests.clientaccess;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuthErrorException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessPolicy;
import org.keycloak.authentication.postauth.PostAuthenticationAction;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.testframework.annotations.InjectAdminClient;
import org.keycloak.testframework.annotations.InjectHttpClient;
import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.InjectUser;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.annotations.TestSetup;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ClientConfig;
import org.keycloak.testframework.realm.GroupBuilder;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.ManagedUser;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.realm.UserConfig;
import org.keycloak.testframework.server.KeycloakUrls;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.ui.page.ErrorPage;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.testframework.ui.webdriver.ManagedWebDriver;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.util.JsonSerialization;

/**
 * Prototype coverage for client access policies evaluated by post-authentication actions: direct grant, refresh,
 * client credentials and browser login.
 */
@KeycloakIntegrationTest
public class ClientAccessPolicyTest {

    private static final String CLIENT_ID = "hr-portal";
    private static final String CLIENT_SECRET = "secret";
    private static final String PASSWORD = "password";
    private static final String HR_ROLE = "hr";
    private static final String HR_GROUP = "/staff/hr";
    private static final String ROLE_POLICY = "hr-by-role";
    private static final String GROUP_POLICY = "hr-by-group";
    private static final String ATTRIBUTE_POLICY = "hr-by-attribute";
    private static final String NEGATED_POLICY = "not-hr";

    @InjectRealm(config = TestRealmConfig.class)
    ManagedRealm realm;

    @InjectOAuthClient(config = TestClientConfig.class)
    OAuthClient oauth;

    @InjectUser(config = AliceConfig.class, ref = "alice")
    ManagedUser alice;

    @InjectUser(config = BobConfig.class, ref = "bob")
    ManagedUser bob;

    @InjectWebDriver
    ManagedWebDriver driver;

    @InjectAdminClient
    Keycloak adminClient;

    @InjectHttpClient
    CloseableHttpClient httpClient;

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @InjectPage
    LoginPage loginPage;

    @InjectPage
    ErrorPage errorPage;

    @TestSetup
    public void setup() {
        RoleRepresentation hr = realm.admin().roles().get(HR_ROLE).toRepresentation();
        realm.admin().users().get(alice.getId()).roles().realmLevel().add(List.of(hr));

        String rolePolicy = createPolicy(ROLE_POLICY);
        createCondition(rolePolicy, "require-role", "hr role", Map.of("roles", List.of(HR_ROLE)));

        String groupPolicy = createPolicy(GROUP_POLICY);
        createCondition(groupPolicy, "require-group", "hr group", Map.of("groups", List.of("/staff"), "includeSubgroups", List.of("true")));

        String attributePolicy = createPolicy(ATTRIBUTE_POLICY);
        createCondition(attributePolicy, "require-attribute", "department attribute",
                Map.of("attribute", List.of("department"), "value", List.of("hr|finance"), "regex", List.of("true"),
                        "includeGroupAttributes", List.of("true")));

        String negatedPolicy = createPolicy(NEGATED_POLICY);
        createCondition(negatedPolicy, "require-role", "not hr role", Map.of("roles", List.of(HR_ROLE), "negate", List.of("true")));

        ComponentRepresentation action = new ComponentRepresentation();
        action.setName("client access policies");
        action.setProviderType(PostAuthenticationAction.class.getName());
        action.setProviderId("client-access-policy");
        action.setConfig(new MultivaluedHashMap<>(Map.of("priority", List.of("10"))));
        try (Response response = realm.admin().components().add(action)) {
            Assertions.assertEquals(201, response.getStatus());
        }
    }

    @Test
    public void directGrantAllowedForUserWithRole() {
        attachPolicies(ROLE_POLICY);
        AccessTokenResponse response = oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword());
        Assertions.assertTrue(response.isSuccess(), response.getErrorDescription());
    }

    @Test
    public void directGrantDeniedForUserWithoutRole() {
        attachPolicies(ROLE_POLICY);
        AccessTokenResponse response = oauth.doPasswordGrantRequest(bob.getUsername(), bob.getPassword());
        Assertions.assertEquals(400, response.getStatusCode());
        Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, response.getError());
    }

    @Test
    public void groupAndAttributePoliciesEvaluated() {
        attachPolicies(GROUP_POLICY + "," + ATTRIBUTE_POLICY);
        Assertions.assertTrue(oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword()).isSuccess());
        Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, oauth.doPasswordGrantRequest(bob.getUsername(), bob.getPassword()).getError());
    }

    @Test
    public void noPoliciesAttachedAllowsEveryone() {
        attachPolicies("");
        Assertions.assertTrue(oauth.doPasswordGrantRequest(bob.getUsername(), bob.getPassword()).isSuccess());
    }

    @Test
    public void unknownPolicyDenies() {
        attachPolicies("does-not-exist");
        Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword()).getError());
    }

    @Test
    public void negatedConditionInvertsResult() {
        attachPolicies(NEGATED_POLICY);
        Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword()).getError());
        Assertions.assertTrue(oauth.doPasswordGrantRequest(bob.getUsername(), bob.getPassword()).isSuccess());
    }

    @Test
    public void policiesListedThroughAdminEndpoint() throws Exception {
        HttpGet get = new HttpGet(keycloakUrls.getAdmin() + "/realms/" + realm.getName() + "/client-access-policies");
        get.setHeader("Authorization", "Bearer " + adminClient.tokenManager().getAccessTokenString());
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
            List<ComponentRepresentation> policies = JsonSerialization.readValue(response.getEntity().getContent(), new TypeReference<>() {});
            List<String> names = policies.stream().map(ComponentRepresentation::getName).toList();
            Assertions.assertTrue(names.containsAll(List.of(ROLE_POLICY, GROUP_POLICY, ATTRIBUTE_POLICY, NEGATED_POLICY)), names.toString());
            Assertions.assertTrue(policies.stream().allMatch(p -> ClientAccessPolicy.class.getName().equals(p.getProviderType())));
        }
    }

    @Test
    public void refreshDeniedAfterRoleRemoved() {
        attachPolicies(ROLE_POLICY);
        AccessTokenResponse login = oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword());
        Assertions.assertTrue(login.isSuccess());

        RoleRepresentation hr = realm.admin().roles().get(HR_ROLE).toRepresentation();
        realm.admin().users().get(alice.getId()).roles().realmLevel().remove(List.of(hr));
        try {
            AccessTokenResponse refresh = oauth.doRefreshTokenRequest(login.getRefreshToken());
            Assertions.assertEquals(400, refresh.getStatusCode());
            Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, refresh.getError());
        } finally {
            realm.admin().users().get(alice.getId()).roles().realmLevel().add(List.of(hr));
        }

        // the client session was removed, so the old refresh token stays unusable even though the role is back
        Assertions.assertEquals(OAuthErrorException.INVALID_GRANT, oauth.doRefreshTokenRequest(login.getRefreshToken()).getError());
        Assertions.assertTrue(oauth.doPasswordGrantRequest(alice.getUsername(), alice.getPassword()).isSuccess());
    }

    @Test
    public void clientCredentialsEvaluatedForServiceAccount() {
        attachPolicies(ROLE_POLICY);
        AccessTokenResponse denied = oauth.doClientCredentialsGrantAccessTokenRequest();
        Assertions.assertEquals(403, denied.getStatusCode());
        Assertions.assertEquals(OAuthErrorException.UNAUTHORIZED_CLIENT, denied.getError());

        RoleRepresentation hr = realm.admin().roles().get(HR_ROLE).toRepresentation();
        String serviceAccountId = clientResource().getServiceAccountUser().getId();
        realm.admin().users().get(serviceAccountId).roles().realmLevel().add(List.of(hr));
        try {
            Assertions.assertTrue(oauth.doClientCredentialsGrantAccessTokenRequest().isSuccess());
        } finally {
            realm.admin().users().get(serviceAccountId).roles().realmLevel().remove(List.of(hr));
        }
    }

    @Test
    public void browserLoginDeniedWithErrorPage() {
        attachPolicies(ROLE_POLICY);
        endBrowserSessions();
        oauth.openLoginForm();
        loginPage.fillLogin(bob.getUsername(), bob.getPassword());
        loginPage.submit();
        errorPage.assertCurrent();
        Assertions.assertEquals("No access", errorPage.getError());
    }

    @Test
    public void browserLoginAllowed() {
        attachPolicies(ROLE_POLICY);
        endBrowserSessions();
        oauth.openLoginForm();
        loginPage.fillLogin(alice.getUsername(), alice.getPassword());
        loginPage.submit();
        Assertions.assertNotNull(oauth.parseLoginResponse().getCode());
    }

    private void endBrowserSessions() {
        realm.admin().users().get(alice.getId()).logout();
        realm.admin().users().get(bob.getId()).logout();
        driver.driver().manage().deleteAllCookies();
    }

    private void attachPolicies(String value) {
        ClientResource client = clientResource();
        ClientRepresentation rep = client.toRepresentation();
        rep.getAttributes().put(ClientAccessPolicy.CLIENT_ATTRIBUTE, value);
        client.update(rep);
    }

    private ClientResource clientResource() {
        String id = realm.admin().clients().findByClientId(CLIENT_ID).get(0).getId();
        return realm.admin().clients().get(id);
    }

    private String createPolicy(String name) {
        ComponentRepresentation policy = new ComponentRepresentation();
        policy.setName(name);
        policy.setProviderType(ClientAccessPolicy.class.getName());
        policy.setProviderId("default");
        try (Response response = realm.admin().components().add(policy)) {
            Assertions.assertEquals(201, response.getStatus());
            return ApiUtil.getCreatedId(response);
        }
    }

    private void createCondition(String policyId, String providerId, String name, Map<String, List<String>> config) {
        ComponentRepresentation condition = new ComponentRepresentation();
        condition.setName(name);
        condition.setParentId(policyId);
        condition.setProviderType(ClientAccessCondition.class.getName());
        condition.setProviderId(providerId);
        condition.setConfig(new MultivaluedHashMap<>(config));
        try (Response response = realm.admin().components().add(condition)) {
            Assertions.assertEquals(201, response.getStatus());
        }
    }

    public static class TestRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.roles(HR_ROLE)
                    .groups(GroupBuilder.create().name("staff")
                            .subGroups(GroupBuilder.create().name("hr").attribute("department", "hr")));
        }
    }

    public static class TestClientConfig implements ClientConfig {
        @Override
        public ClientBuilder configure(ClientBuilder client) {
            return client.clientId(CLIENT_ID)
                    .secret(CLIENT_SECRET)
                    .directAccessGrantsEnabled()
                    .serviceAccountsEnabled()
                    .attribute(ClientAccessPolicy.CLIENT_ATTRIBUTE, ROLE_POLICY);
        }
    }

    public static class AliceConfig implements UserConfig {
        @Override
        public UserBuilder configure(UserBuilder user) {
            return user.username("alice").password(PASSWORD).name("Alice", "HR").email("alice@example.org")
                    .groups(HR_GROUP);
        }
    }

    public static class BobConfig implements UserConfig {
        @Override
        public UserBuilder configure(UserBuilder user) {
            return user.username("bob").password(PASSWORD).name("Bob", "Sales").email("bob@example.org");
        }
    }
}
