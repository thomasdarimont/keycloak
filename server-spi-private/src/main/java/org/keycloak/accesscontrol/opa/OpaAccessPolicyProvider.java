package org.keycloak.accesscontrol.opa;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriBuilder;
import org.keycloak.accesscontrol.AccessDecision;
import org.keycloak.accesscontrol.AccessDecisionContext;
import org.keycloak.accesscontrol.AccessPolicyProvider;
import org.keycloak.accesscontrol.opa.client.OpaClient;
import org.keycloak.accesscontrol.opa.client.OpaPolicyQuery;
import org.keycloak.accesscontrol.opa.client.OpaRequest;
import org.keycloak.accesscontrol.opa.client.OpaRequestContext;
import org.keycloak.accesscontrol.opa.client.OpaResource;
import org.keycloak.accesscontrol.opa.client.OpaResponse;
import org.keycloak.accesscontrol.opa.client.OpaSubject;
import org.keycloak.config.ClientConfig;
import org.keycloak.config.ConfigWrapper;
import org.keycloak.config.MapConfig;
import org.keycloak.config.RealmConfig;
import org.keycloak.models.ClientModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.RoleUtils;
import org.keycloak.utils.StringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpaAccessPolicyProvider implements AccessPolicyProvider {

    public static final String ID = "opa";

    public static final String ACCESS_DENIED_MESSAGE = "access-denied";

    public static final String ACTION_LOGIN = "login";

    public static final String ACTION_CHECK_ACCESS = "access";

    enum Option {
        USE_REALM_ROLES("use-realm-roles"), //
        USE_CLIENT_ROLES("use-client-roles"), //
        USER_ATTRIBUTES("user-attributes"), //
        CONTEXT_ATTRIBUTES("context-attributes"), //
        REALM_ATTRIBUTES("realm-attributes"), //
        CLIENT_ATTRIBUTES("client-attributes"), //
        REQUEST_HEADERS("request-headers"), //
        USE_GROUPS("use-groups"), //
        URL("url"), //
        POLICY_PATH("policy-path"), //
        ;

        private final String key;

        Option(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    private static final Pattern COMMA_PATTERN = Pattern.compile(",");

    private final Map<String, Object> providerConfig;

    public OpaAccessPolicyProvider(Map<String, Object> providerConfig) {
        this.providerConfig = providerConfig;
    }

    @Override
    public AccessDecision evaluate(AccessDecisionContext context) {

        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        ClientModel client = context.getClient();
        KeycloakSession session = context.getSession();
        String action = ACTION_CHECK_ACCESS;

        ConfigWrapper config = new MapConfig(providerConfig);
        OpaSubject subject = createSubject(user, client, config);
        OpaResource resource = createResource(realm, client, config);
        OpaRequestContext requestContext = createRequestContext(session, config);

        String policyUrl = createPolicyUrl(realm, client, action, config);
        OpaClient opaClient = createOpaClient(context);
        OpaPolicyQuery policyQuery = createPolicyRequest(subject, resource, requestContext, action);

        OpaResponse policyResponse = opaClient.evaluatePolicy(policyUrl, new OpaRequest(policyQuery));

        return toAccessDecision(policyResponse);
    }

    protected AccessDecision toAccessDecision(OpaResponse response) {
        return new AccessDecision(response.isAllowed(), response.getMetadata(), response.getMessage());
    }

    protected OpaPolicyQuery createPolicyRequest(OpaSubject subject, OpaResource resource, OpaRequestContext requestContext, String action) {
        OpaPolicyQuery query = new OpaPolicyQuery();
        query.setSubject(subject);
        query.setResource(resource);
        query.setContext(requestContext);
        query.setAction(action);
        return query;
    }

    protected OpaSubject createSubject(UserModel user, ClientModel client, ConfigWrapper config) {
        OpaSubject subject = new OpaSubject();
        subject.setId(user.getId());
        subject.setUsername(user.getUsername());
        subject.setRealmRoles(config.getBoolean(Option.USE_REALM_ROLES.key, true) ? fetchRealmRoles(user) : null);
        subject.setClientRoles(config.getBoolean(Option.USE_CLIENT_ROLES.key, true) ? fetchClientRoles(user, client) : null);
        subject.setAttributes(config.isConfigured(Option.USER_ATTRIBUTES.key, true) ? extractUserAttributes(user, config) : null);
        subject.setGroups(config.getBoolean(Option.USE_GROUPS.key, true) ? fetchGroupNames(user) : null);
        return subject;
    }

    protected OpaResource createResource(RealmModel realm, ClientModel client, ConfigWrapper config) {
        OpaResource resource = new OpaResource();
        resource.setRealm(realm.getName());
        resource.setRealmAttributes(config.isConfigured(Option.REALM_ATTRIBUTES.key, false) ? extractRealmAttributes(realm, config) : null);
        resource.setClientId(client.getClientId());
        resource.setClientAttributes(config.isConfigured(Option.CLIENT_ATTRIBUTES.key, false) ? extractClientAttributes(client, config) : null);
        return resource;
    }

    protected OpaClient createOpaClient(AccessDecisionContext context) {
        return new OpaClient(context.getSession());
    }

    protected String createPolicyUrl(RealmModel realm, ClientModel client, String action, ConfigWrapper config) {

        String opaUrl = config.getString(Option.URL.key);

        if (opaUrl == null) {
            throw new RuntimeException("missing opaUrl");
        }

        String policyPath = createPolicyPath(realm, client, action, config);

        return opaUrl + policyPath;
    }

    protected String createPolicyPath(RealmModel realm, ClientModel client, String action, ConfigWrapper config) {
        String policyPathTemplate = config.getString(Option.POLICY_PATH.key);
        Map<String, String> params = new HashMap<>();
        params.put("realm", realm.getName());
        params.put("action", action);
        params.put("client", client.getClientId());
        return UriBuilder.fromPath(policyPathTemplate).buildFromMap(params).toString();
    }

    protected OpaRequestContext createRequestContext(KeycloakSession session, ConfigWrapper config) {
        Map<String, Object> contextAttributes = config.isConfigured(Option.CONTEXT_ATTRIBUTES.key, false) ? extractContextAttributes(session, config) : null;
        Map<String, Object> headers = config.isConfigured(Option.REQUEST_HEADERS.key, false) ? extractRequestHeaders(session, config) : null;
        return new OpaRequestContext(contextAttributes, headers);
    }

    protected Map<String, Object> extractRequestHeaders(KeycloakSession session, ConfigWrapper config) {

        String headerNames = config.getValue(Option.REQUEST_HEADERS.key);
        if (headerNames == null || StringUtil.isBlank(headerNames)) {
            return null;
        }

        HttpHeaders requestHeaders = session.getContext().getRequestHeaders();
        Map<String, Object> headers = new HashMap<>();
        for (String header : COMMA_PATTERN.split(headerNames.trim())) {
            String value = requestHeaders.getHeaderString(header);
            headers.put(header, value);
        }

        if (headers.isEmpty()) {
            return null;
        }

        return headers;
    }

    protected Map<String, Object> extractContextAttributes(KeycloakSession session, ConfigWrapper config) {
        return extractAttributes(null, config, Option.CONTEXT_ATTRIBUTES.key, (source, attr) -> {
            Object value;
            switch (attr) {
                case "remoteAddress":
                    value = session.getContext().getConnection().getRemoteAddr();
                    break;
                default:
                    value = null;
            }

            return value;
        }, u -> null);
    }

    protected <T> Map<String, Object> extractAttributes(T source, ConfigWrapper config, String attributesKey, BiFunction<T, String, Object> valueExtractor, Function<T, Map<String, Object>> defaultValuesExtractor) {

        if (config == null) {
            return defaultValuesExtractor.apply(source);
        }

        String attributeNames = config.getValue(attributesKey);
        if (attributeNames == null || StringUtil.isBlank(attributeNames)) {
            return defaultValuesExtractor.apply(source);
        }

        Map<String, Object> attributes = new HashMap<>();
        for (String attributeName : COMMA_PATTERN.split(attributeNames.trim())) {
            Object value = valueExtractor.apply(source, attributeName);
            attributes.put(attributeName, value);
        }

        return attributes;
    }

    protected Map<String, Object> extractUserAttributes(UserModel user, ConfigWrapper config) {

        return extractAttributes(user, config, Option.USER_ATTRIBUTES.key, (u, attr) -> {
            Object value;
            switch (attr) {
                // handle built-in attributes
                case "email":
                    value = user.getEmail();
                    break;
                case "emailVerified":
                    value = user.isEmailVerified();
                    break;
                case "createdTimestamp":
                    value = user.getCreatedTimestamp();
                    break;
                case "lastName":
                    value = user.getLastName();
                    break;
                case "firstName":
                    value = user.getFirstName();
                    break;
                case "federationLink":
                    value = user.getFederationLink();
                    break;
                case "serviceAccountLink":
                    value = user.getServiceAccountClientLink();
                    break;
                // handle generic attributes
                default:
                    value = user.getFirstAttribute(attr);
                    break;
            }
            return value;
        }, this::extractDefaultUserAttributes);
    }

    protected Map<String, Object> extractDefaultUserAttributes(UserModel user) {
        Map<String, Object> userAttributes = new HashMap<>();
        userAttributes.put("email", user.getEmail());
        return userAttributes;
    }

    protected Map<String, Object> extractClientAttributes(ClientModel client, ConfigWrapper config) {
        ClientConfig clientConfig = new ClientConfig(client);
        return extractAttributes(client, config, Option.CLIENT_ATTRIBUTES.key, (c, attr) -> clientConfig.getValue(attr), c -> null);
    }

    protected Map<String, Object> extractRealmAttributes(RealmModel realm, ConfigWrapper config) {
        RealmConfig realmConfig = new RealmConfig(realm);
        return extractAttributes(realm, config, Option.REALM_ATTRIBUTES.key, (r, attr) -> realmConfig.getValue(attr), r -> null);
    }

    protected List<String> fetchGroupNames(UserModel user) {
        List<String> groupNames = user.getGroupsStream().map(GroupModel::getName).collect(Collectors.toList());
        return groupNames.isEmpty() ? null : groupNames;
    }

    protected List<String> fetchClientRoles(UserModel user, ClientModel client) {
        Stream<RoleModel> explicitClientRoles = RoleUtils.expandCompositeRolesStream(user.getClientRoleMappingsStream(client));
        Stream<RoleModel> implicitClientRoles = RoleUtils.expandCompositeRolesStream(user.getRealmRoleMappingsStream());
        return Stream.concat(explicitClientRoles, implicitClientRoles) //
                .filter(RoleModel::isClientRole) //
                .map(this::normalizeRoleName) //
                .collect(Collectors.toList());
    }

    protected List<String> fetchRealmRoles(UserModel user) {
        return RoleUtils.expandCompositeRolesStream(user.getRealmRoleMappingsStream()) //
                .filter(r -> !r.isClientRole()).map(this::normalizeRoleName) //
                .collect(Collectors.toList());
    }

    protected String normalizeRoleName(RoleModel role) {
        if (role.isClientRole()) {
            return ((ClientModel) role.getContainer()).getClientId() + ":" + role.getName();
        }
        return role.getName();
    }
}
