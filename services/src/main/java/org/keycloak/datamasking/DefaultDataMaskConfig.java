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

import org.keycloak.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultDataMaskConfig implements DataMaskConfig {

    public static final String EMAIL_MASK_SUFFIX = "@XXXXX.com";
    public static final String IP_ADDRESS_MIDDLE_PART = ".X.X.";
    public static final String USERNAME_MASK_SUFFIX = "XXXXX";
    public static final String DEFAULT_DATA_MASK = "XXXXX";

    private final Map<String, String> fieldMaskingRules;
    private final String emailMaskSuffix;
    private final String ipAddressPart;
    private final String usernameMaskSuffix;
    private final String defaultDataMask;

    public DefaultDataMaskConfig(Config.Scope maskingConfig) {

        this.emailMaskSuffix = maskingConfig.get("emailMaskSuffix", EMAIL_MASK_SUFFIX);
        this.ipAddressPart = maskingConfig.get("ipAddressPart", IP_ADDRESS_MIDDLE_PART);
        this.usernameMaskSuffix = maskingConfig.get("usernameMaskSuffix", USERNAME_MASK_SUFFIX);
        this.defaultDataMask = maskingConfig.get("defaultDataMask", DEFAULT_DATA_MASK);

        String[] maskedFields = maskingConfig.getArray("maskedFields");
        if (maskedFields != null) {
            Map<String, String> map = new HashMap<>();
            for (String field : maskedFields) {
                map.put(field, maskingConfig.get(field, field));
            }
            fieldMaskingRules = map;
        } else {
            fieldMaskingRules = Collections.emptyMap();
        }
    }

    public Map<String, String> getFieldMaskingRules() {
        return fieldMaskingRules;
    }

    public String getEmailMaskSuffix() {
        return emailMaskSuffix;
    }

    public String getIpAddressPart() {
        return ipAddressPart;
    }

    public String getUsernameMaskSuffix() {
        return usernameMaskSuffix;
    }

    public String getDefaultDataMask() {
        return defaultDataMask;
    }
}
