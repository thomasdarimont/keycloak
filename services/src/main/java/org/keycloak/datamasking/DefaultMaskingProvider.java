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

import org.jboss.resteasy.util.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link MaskingProvider} that provides some built-in masking rules.
 */
public class DefaultMaskingProvider implements MaskingProvider {

    private static final Pattern LITERAL_DOT_PATTERN = Pattern.compile("\\.");

    private final Map<String, String> fieldMaskingRules;
    private final String emailMaskSuffix;
    private final String ipAddressPart;
    private final String usernameMaskSuffix;
    private final String defaultDataMask;

    public DefaultMaskingProvider(DataMaskConfig config) {
        this.emailMaskSuffix = config.getEmailMaskSuffix();
        this.ipAddressPart = config.getIpAddressPart();
        this.usernameMaskSuffix = config.getUsernameMaskSuffix();
        this.defaultDataMask = config.getDefaultDataMask();
        this.fieldMaskingRules = config.getFieldMaskingRules();
    }

    @Override
    public String mask(String input, String maskingHint) {

        if (input == null) {
            return null;
        }

        if (input.isEmpty()) {
            return input;
        }

        return maskInternal(input, maskingHint);
    }

    protected String maskInternal(String input, String maskingHint) {

        String key = resolveMaskingRuleKey(maskingHint);
        if (key == null) {
            return input;
        }
        String maskingRule = fieldMaskingRules.get(key);
        if (maskingRule == null) {
            return input;
        }

        return applyMaskingRule(input, maskingRule);
    }

    protected String applyMaskingRule(String input, String maskingRule) {

        String output;

        switch (maskingRule) {
            case MaskingRules.EMAIL:
                output = maskEmail(input);
                break;
            case MaskingRules.USERNAME:
                output = maskUsername(input);
                break;
            case MaskingRules.IP_ADDRESS:
                output = maskIpAddress(input);
                break;
            case MaskingRules.HASH:
                output = hash(input);
                break;
            case MaskingRules.DEFAULT:
                output = defaultDataMask;
                break;
            default:
                output = input;
                break;
        }

        return output;
    }

    protected String resolveMaskingRuleKey(String inputHint) {
        if (inputHint == null) {
            return MaskingRules.DEFAULT;
        }
        return inputHint;
    }

    protected String maskIpAddress(String input) {

        String[] octets = LITERAL_DOT_PATTERN.split(input);
        return octets[0] + ipAddressPart + octets[3];
    }

    protected String maskUsername(String input) {
        return input.charAt(0) + usernameMaskSuffix;
    }

    protected String maskEmail(String input) {
        return input.charAt(0) + emailMaskSuffix;
    }

    protected String hash(String input) {
        return Hex.encodeHex(hash(input.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hash(byte[] inputBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(inputBytes);
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Could not compute hash.", e);
        }
    }

}
