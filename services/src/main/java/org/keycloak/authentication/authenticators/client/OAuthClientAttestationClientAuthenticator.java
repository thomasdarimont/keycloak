package org.keycloak.authentication.authenticators.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.OAuthErrorException;
import org.keycloak.TokenVerifier;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.ClientAuthenticationFlowContext;
import org.keycloak.common.Profile;
import org.keycloak.common.VerificationException;
import org.keycloak.crypto.AsymmetricSignatureVerifierContext;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.ServerECDSASignatureVerifierContext;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKParser;
import org.keycloak.jose.jws.Algorithm;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.keys.loader.PublicKeyStorageManager;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.protocol.oauth2.attestation.AttestationChallenge;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.ServicesLogger;
import org.keycloak.util.JWKSUtils;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OAuthClientAttestationClientAuthenticator extends AbstractClientAuthenticator implements EnvironmentDependentProviderFactory {

    public static final String OAUTH_CLIENT_ATTESTATION_HEADER = "OAuth-Client-Attestation";
    public static final String OAUTH_CLIENT_ATTESTATION_POP_HEADER = "OAuth-Client-Attestation-PoP";

    public static final String PROVIDER_ID = "client-attestation";
    public static final String ATTR_PREFIX = "jwt.credential";
    public static final String CERTIFICATE_ATTR = "jwt.credential.certificate";

    public static final String ALLOWED_ISSUER_ATTR = "clientattest.issuer";

    public static final String CLIENT_ATTESTATION_JWKS = "clientattest.jwks";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "OAuth 2.0 Client Attestation";
    }

    @Override
    public String getHelpText() {
        return "OAuth 2.0 Attestation based Client Authentication validates client based on signed client attestation JWT issued by a Client Attester and signed with the Client private key. See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-06";
    }

    @Override
    public void authenticateClient(ClientAuthenticationFlowContext context) {


        var httpHeaders = context.getHttpRequest().getHttpHeaders();

        String clientAttestationHeader = httpHeaders.getHeaderString(OAUTH_CLIENT_ATTESTATION_HEADER);
        if (clientAttestationHeader == null || clientAttestationHeader.isEmpty()) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(), OAuthErrorException.INVALID_CLIENT, "Client authentication with client attestation failed: missing client attestation");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, challengeResponse);
            return;
        }

        String clientAttestationPopHeader = httpHeaders.getHeaderString(OAUTH_CLIENT_ATTESTATION_POP_HEADER);

        String oauthClientAttestation = clientAttestationHeader;
        String oauthClientAttestationPoP = clientAttestationPopHeader;
        // handle Concatenated Serialization for Client Attestations
        // see: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-07#section-7
        if (clientAttestationHeader.contains("~")) {
            // ignore clientAttestationPopHeader and split clientAttes
            String[] attestationAndPop = clientAttestationHeader.split("~");
            oauthClientAttestation = attestationAndPop[0];
            oauthClientAttestationPoP = attestationAndPop[1];
        }

        // parse client attestation
        JsonWebToken clientAttestation;
        Algorithm clientAttestationAlg;
        try {
            var clientAttestationJws = new JWSInput(oauthClientAttestation);
            clientAttestation = clientAttestationJws.readJsonContent(JsonWebToken.class);
            clientAttestationAlg = clientAttestationJws.getHeader().getAlgorithm();
        } catch (JWSInputException e) {
            ServicesLogger.LOGGER.errorValidatingAssertion(e);
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(), OAuthErrorException.INVALID_CLIENT, "Client authentication with client attestation failed: " + e.getMessage());
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, challengeResponse);
            return;
        }

        String clientId = clientAttestation.getSubject();
        if (clientId == null) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(), "invalid_client", "Missing sub claim");
            context.challenge(challengeResponse);
            return;
        }

        context.getEvent().client(clientId);

        ClientModel client = context.getSession().clients().getClientByClientId(context.getRealm(), clientId);
        if (client == null) {
            context.failure(AuthenticationFlowError.CLIENT_NOT_FOUND, null);
            return;
        }

        context.setClient(client);

        if (!client.isEnabled()) {
            context.failure(AuthenticationFlowError.CLIENT_DISABLED, null);
            return;
        }

        // extract iss claim from client attestation
        String issuer = clientAttestation.getIssuer();
        String allowedIssuer = client.getAttribute(ALLOWED_ISSUER_ATTR);

        if (!allowedIssuer.endsWith(issuer)) {
            context.getEvent().detail("client_attestation_issuer", issuer);
            context.getEvent().error("invalid_client_attestation_issuer");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        // extract cnf claim from clientAttestation
        var cnf = (Map<String, Object>) clientAttestation.getOtherClaims().get("cnf");
        if (cnf == null) {
            context.getEvent().error("invalid_client_attestation_cnf_missing");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        // extract jwk from cnf
        var jwkMap = (Map<String, Object>) cnf.get("jwk");
        if (jwkMap == null) {
            context.getEvent().error("invalid_client_attestation_cnf_jwk_missing");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        // create JWKS from jwk
        KeyWrapper keyWrapper;
        try {
            String jwkString = JsonSerialization.writeValueAsString(jwkMap);
            JWKParser parser = JWKParser.create().parse(jwkString);
            JWK jwk = parser.getJwk();
            keyWrapper = JWKSUtils.getKeyWrapper(jwk);
        } catch (IOException e) {
            context.getEvent().error("invalid_client_attestation_invalid_jwk");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        // verify signature of clientAttestationPop with JWKS
        JsonWebToken clientAttestationPop;
        try {
            // TODO fix creation of verifierContext
            AsymmetricSignatureVerifierContext verifierContext = switch (clientAttestationAlg.name()) {
                case org.keycloak.crypto.Algorithm.ES256, org.keycloak.crypto.Algorithm.ES384,
                     org.keycloak.crypto.Algorithm.ES512 ->
                        verifierContext = new ServerECDSASignatureVerifierContext(keyWrapper);
                default -> verifierContext = new AsymmetricSignatureVerifierContext(keyWrapper);
            };

            TokenVerifier<JsonWebToken> popVerifier = TokenVerifier.create(oauthClientAttestationPoP, JsonWebToken.class)
                    .verifierContext(verifierContext);
            popVerifier.verify();
            clientAttestationPop = popVerifier.getToken();
        } catch (VerificationException e) {
            context.getEvent().error("invalid_client_attestation_invalid_pop_signature");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        // verify challenge
        String nonce = (String)clientAttestationPop.getOtherClaims().get("nonce");
        RealmModel realm = context.getRealm();
        SingleUseObjectProvider singleUse = context.getSession().getProvider(SingleUseObjectProvider.class);
        Map<String, String> challengeNotes = singleUse.get(AttestationChallenge.generateChallengeKey(realm, nonce));
        if (challengeNotes == null) {
            context.getEvent().error("invalid_client_attestation_challenge");
            context.failure(AuthenticationFlowError.INVALID_CLIENT_CREDENTIALS, null);
            return;
        }

        context.success();
    }

    protected PublicKey getSignatureValidationKey(ClientModel client, ClientAuthenticationFlowContext context, JWSInput jws) {
        PublicKey publicKey = PublicKeyStorageManager.getClientPublicKey(context.getSession(), client, jws);
        if (publicKey == null) {
            Response challengeResponse = ClientAuthUtil.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(), OAuthErrorException.INVALID_CLIENT, "Unable to load public key");
            context.failure(AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED, challengeResponse);
            return null;
        } else {
            return publicKey;
        }
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public List<ProviderConfigProperty> getConfigPropertiesPerClient() {
        var list = ProviderConfigurationBuilder.create() //
                .property().name(ALLOWED_ISSUER_ATTR) //
                .type(ProviderConfigProperty.STRING_TYPE) //
                .label("Allowed Issuer") //
                .defaultValue(null) //
                .helpText("Allowed issuer of the client attestation.") //
                .add() //

                .property().name(CLIENT_ATTESTATION_JWKS) //
                .type(ProviderConfigProperty.TEXT_TYPE) //
                .label("Client Attestation JWKS") //
                .defaultValue(null) //
                .helpText("JWKS for the Client Attestation") //
                .add() //

                .build();

        return list;
    }

    @Override
    public Map<String, Object> getAdapterConfiguration(ClientModel client) {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> getProtocolAuthenticatorMethods(String loginProtocol) {
        if (loginProtocol.equals(OIDCLoginProtocol.LOGIN_PROTOCOL)) {
            Set<String> results = new HashSet<>();
            results.add(OIDCLoginProtocol.OAUTH2_CLIENT_ATTESTATION);
            return results;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.OAUTH_ABCA);
    }
}
