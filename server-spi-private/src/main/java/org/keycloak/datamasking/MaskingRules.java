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

package org.keycloak.datamasking;

/**
 * A list of built-in Masking rules.
 */
public class MaskingRules {

    /**
     * The default data masking rule.
     */
    public static final String DEFAULT = "default";

    /**
     * The data masking rule for usernames.
     */
    public static final String USERNAME = "username";

    /**
     * The data masking rule for email addresses.
     */
    public static final String EMAIL = "email";

    /**
     * The data masking rule for IP addresses.
     */
    public static final String IP_ADDRESS = "ipAddress";

    /**
     * The data masking rule that hashes a value.
     */
    public static final String HASH = "hash";
}
