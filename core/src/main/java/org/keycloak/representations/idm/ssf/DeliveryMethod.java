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
package org.keycloak.representations.idm.ssf;

import com.fasterxml.jackson.annotation.JsonValue;

import java.net.URI;

public enum DeliveryMethod {
        PUSH_BASED("urn:ietf:rfc:8935")
        , POLL_BASED("urn:ietf:rfc:8936")
        ;

        private final String specUrn;

        DeliveryMethod(String specUrn) {
            this.specUrn = specUrn;
        }

        @JsonValue
        public String getSpecUrn() {
            return specUrn;
        }

        public URI toUri() {
            return URI.create(specUrn);
        }
    }
