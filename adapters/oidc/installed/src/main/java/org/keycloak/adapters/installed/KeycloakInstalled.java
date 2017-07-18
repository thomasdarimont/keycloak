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

package org.keycloak.adapters.installed;

import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.rotation.AdapterRSATokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class KeycloakInstalled {

    private static final String KEYCLOAK_JSON = "META-INF/keycloak.json";

    private static final String LOGIN_STATUS_PARAM = "login-status";

    private static final String ERROR_DESCRIPTION_PARAM = "error-description";

    private KeycloakDeployment deployment;

    private enum LoginMode {
        MANUAL, DESKTOP
    }

    public enum LoginStatus {
        LOGGED_IN, LOGGED_OUT
    }

    private String tokenString;
    private String idTokenString;
    private IDToken idToken;
    private AccessToken token;
    private String refreshToken;
    private LoginMode loginMode;
    private LoginStatus loginStatus;

    private HttpResponseWriter httpResponseWriter = DefaultHttpResponseWriter.INSTANCE;

    private Locale locale;

    public KeycloakInstalled() {
        this(Thread.currentThread().getContextClassLoader().getResourceAsStream(KEYCLOAK_JSON));
    }

    public KeycloakInstalled(InputStream config) {

        this.deployment = KeycloakDeploymentBuilder.build(config);
        this.locale = Locale.getDefault();
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public HttpResponseWriter getHttpResponseWriter() {
        return httpResponseWriter;
    }

    public void setHttpResponseWriter(HttpResponseWriter httpResponseWriter) {
        this.httpResponseWriter = Objects.requireNonNull(httpResponseWriter, "httpResponseWriter");
    }

    public LoginStatus getLoginStatus() {
        return loginStatus;
    }

    public void login() throws IOException, ServerRequest.HttpFailure, VerificationException, InterruptedException, OAuthErrorException, URISyntaxException {
        if (isDesktopSupported()) {
            loginDesktop();
        } else {
            loginManual();
        }
    }

    public void login(PrintStream printer, Reader reader) throws IOException, ServerRequest.HttpFailure, VerificationException, InterruptedException, OAuthErrorException, URISyntaxException {
        if (isDesktopSupported()) {
            loginDesktop();
        } else {
            loginManual(printer, reader);
        }
    }

    public void logout() throws IOException, InterruptedException, URISyntaxException {
        if (loginMode == LoginMode.DESKTOP) {
            logoutDesktop();
        }

        tokenString = null;
        token = null;

        idTokenString = null;
        idToken = null;

        refreshToken = null;

        loginMode = null;
    }

    public void loginDesktop() throws IOException, VerificationException, OAuthErrorException, URISyntaxException, ServerRequest.HttpFailure, InterruptedException {
        CallbackListener callback = new CallbackListener();
        callback.start();

        String redirectUri = KeycloakUriBuilder.fromUri("http://localhost:" + callback.server.getLocalPort()) //
          .queryParam(LOGIN_STATUS_PARAM, LoginStatus.LOGGED_IN.name()) //
          .build().toString();

        String state = UUID.randomUUID().toString();

        String authUrl = deployment.getAuthUrl().clone()
                .queryParam(OAuth2Constants.RESPONSE_TYPE, OAuth2Constants.CODE)
                .queryParam(OAuth2Constants.CLIENT_ID, deployment.getResourceName())
                .queryParam(OAuth2Constants.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2Constants.STATE, state)
                .queryParam(OAuth2Constants.SCOPE, OAuth2Constants.SCOPE_OPENID)
                .queryParam(OAuth2Constants.UI_LOCALES_PARAM, locale.getLanguage())
                .build().toString();

        Desktop.getDesktop().browse(new URI(authUrl));

        callback.join();

        if (!state.equals(callback.state)) {
            throw new VerificationException("Invalid state");
        }

        if (callback.error != null) {
            throw new OAuthErrorException(callback.error, callback.errorDescription);
        }

        if (callback.errorException != null) {
            throw callback.errorException;
        }

        processCode(callback.code, redirectUri);

        loginMode = LoginMode.DESKTOP;
    }

    private void logoutDesktop() throws IOException, URISyntaxException, InterruptedException {
        CallbackListener callback = new CallbackListener();
        callback.start();

        String redirectUri = KeycloakUriBuilder.fromUri("http://localhost:" + callback.server.getLocalPort()) //
          .queryParam(LOGIN_STATUS_PARAM, LoginStatus.LOGGED_OUT.name()) //
          .build().toString();

        String logoutUrl = deployment.getLogoutUrl()
                .queryParam(OAuth2Constants.REDIRECT_URI, redirectUri)
                .build().toString();

        Desktop.getDesktop().browse(new URI(logoutUrl));

        callback.join();

        if (callback.errorException != null) {
            throw callback.errorException;
        }
    }

    public void loginManual() throws IOException, ServerRequest.HttpFailure, VerificationException {
        loginManual(System.out, new InputStreamReader(System.in));
    }

    public void loginManual(PrintStream printer, Reader reader) throws IOException, ServerRequest.HttpFailure, VerificationException {
        CallbackListener callback = new CallbackListener();
        callback.start();

        String redirectUri = KeycloakUriBuilder.fromUri("urn:ietf:wg:oauth:2.0:oob") //
          .queryParam(LOGIN_STATUS_PARAM, LoginStatus.LOGGED_IN.name()) //
          .build().toString();

        String authUrl = deployment.getAuthUrl().clone()
                .queryParam(OAuth2Constants.RESPONSE_TYPE, OAuth2Constants.CODE)
                .queryParam(OAuth2Constants.CLIENT_ID, deployment.getResourceName())
                .queryParam(OAuth2Constants.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2Constants.SCOPE, OAuth2Constants.SCOPE_OPENID)
                .queryParam(OAuth2Constants.UI_LOCALES_PARAM, locale.getLanguage())
                .build().toString();

        printer.println("Open the following URL in a browser. After login copy/paste the code back and press <enter>");
        printer.println(authUrl);
        printer.println();
        printer.print("Code: ");

        String code = readCode(reader);
        processCode(code, redirectUri);

        loginMode = LoginMode.MANUAL;
    }

    public String getTokenString() throws VerificationException, IOException, ServerRequest.HttpFailure {
        return tokenString;
    }

    public String getTokenString(long minValidity, TimeUnit unit) throws VerificationException, IOException, ServerRequest.HttpFailure {
        long expires = ((long) token.getExpiration()) * 1000 - unit.toMillis(minValidity);
        if (expires < System.currentTimeMillis()) {
            refreshToken();
        }

        return tokenString;
    }

    public void refreshToken() throws IOException, ServerRequest.HttpFailure, VerificationException {
        AccessTokenResponse tokenResponse = ServerRequest.invokeRefresh(deployment, refreshToken);
        parseAccessToken(tokenResponse);
    }

    private void parseAccessToken(AccessTokenResponse tokenResponse) throws VerificationException {
        tokenString = tokenResponse.getToken();
        refreshToken = tokenResponse.getRefreshToken();
        idTokenString = tokenResponse.getIdToken();

        token = AdapterRSATokenVerifier.verifyToken(tokenString, deployment);
        if (idTokenString != null) {
            try {
                JWSInput input = new JWSInput(idTokenString);
                idToken = input.readJsonContent(IDToken.class);
            } catch (JWSInputException e) {
                throw new VerificationException();
            }
        }
    }

    public AccessToken getToken() {
        return token;
    }

    public IDToken getIdToken() {
        return idToken;
    }

    public String getIdTokenString() {
        return idTokenString;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isDesktopSupported() {
        return Desktop.isDesktopSupported();
    }



    public KeycloakDeployment getDeployment() {
        return deployment;
    }

    private void processCode(String code, String redirectUri) throws IOException, ServerRequest.HttpFailure, VerificationException {
        AccessTokenResponse tokenResponse = ServerRequest.invokeAccessCodeToToken(deployment, code, redirectUri, null);
        parseAccessToken(tokenResponse);
    }

    private String readCode(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();

        char cb[] = new char[1];
        while (reader.read(cb) != -1) {
            char c = cb[0];
            if ((c == ' ') || (c == '\n') || (c == '\r')) {
                break;
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public class CallbackListener extends Thread {

        private ServerSocket server;

        private String code;

        private String error;

        private String errorDescription;

        private IOException errorException;

        private String state;

        public CallbackListener() throws IOException {
            server = new ServerSocket(0);
        }

        @Override
        public void run() {

            try (Socket socket = server.accept()){

                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String requestLine = br.readLine();

                String requestPath = requestLine.split(" ")[1];
                parseUrlParameters(requestPath);

                PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                httpResponseWriter.write(pw, KeycloakInstalled.this);
                pw.flush();
            } catch (IOException e) {
                errorException = e;
            }

            try {
                server.close();
            } catch (IOException ignored) {
            }
        }

        private void parseUrlParameters(String url) {

            if (url.indexOf('?') < 0) {
                return;
            }

            String query = url.split("\\?")[1];
            String[] params = query.split("&");

            for (String param : params) {
                String[] keyValue = param.split("=");
                switch (keyValue[0]) {
                    case OAuth2Constants.CODE:
                        code = keyValue[1];
                        break;
                    case OAuth2Constants.ERROR:
                        error = keyValue[1];
                        break;
                    case ERROR_DESCRIPTION_PARAM:
                        errorDescription = keyValue[1];
                        break;
                    case OAuth2Constants.STATE:
                        state = keyValue[1];
                        break;
                    case LOGIN_STATUS_PARAM:
                        loginStatus = LoginStatus.valueOf(keyValue[1].toUpperCase());
                        break;
                    default:
                        //ignore
                }
            }
        }
    }

    public interface HttpResponseWriter {
        void write(PrintWriter responseWriter, KeycloakInstalled keycloak);
    }

    private static class DefaultHttpResponseWriter implements HttpResponseWriter {

        private static final HttpResponseWriter INSTANCE = new DefaultHttpResponseWriter();

        @Override
        public void write(PrintWriter responseWriter, KeycloakInstalled keycloak) {

            responseWriter.println("HTTP/1.1 200 OK");
            responseWriter.println();

            switch(keycloak.getLoginStatus()) {
                case LOGGED_IN:
                    responseWriter.println("<html><h1>Login completed.</h1><div>Please close this browser tab.</div></html>");
                    break;
                case LOGGED_OUT:
                    responseWriter.println("<html><h1>Logout completed.</h1><div>Please close this browser tab.</div></html>");
                    break;
                default:
                    //ignore
            }
        }
    }
}
