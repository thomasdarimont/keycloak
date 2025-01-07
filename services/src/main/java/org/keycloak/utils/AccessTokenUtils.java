/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.keycloak.representations.AccessToken;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

public class AccessTokenUtils {

    private static final Pattern PATTERN_SPACE = Pattern.compile(" ");

    @JsonIgnore
    public static Set<String> splitScopes(AccessToken token) {

        if (token == null) {
            return Collections.emptySet();
        }

        String scope = token.getScope();
        if (scope == null) {
            return Collections.emptySet();
        }

        String[] scopeEntries = PATTERN_SPACE.split(scope);
        return Set.of(scopeEntries);
    }

    public static boolean hasScope(AccessToken token, String scope) {

        if (token == null || scope == null) {
            return false;
        }

        Set<String> strings = splitScopes(token);
        return strings.contains(scope);
    }

    public static boolean hasAnyScope(AccessToken token, String... scopes) {

        if (token == null || scopes == null || scopes.length == 0) {
            return false;
        }

        Set<String> strings = splitScopes(token);
        for (String scope : scopes) {
            if (strings.contains(scope)) {
                return true;
            }
        }
        return false;
    }
}
