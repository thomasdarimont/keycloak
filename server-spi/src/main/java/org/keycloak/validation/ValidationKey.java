/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Denotes a validatable property, e.g. Realm Attributes, User Properties, Client Properties, etc.
 * <p>
 * Users can create custom {@link ValidationKey ValidationKey's} by implementing this interface.
 * It is recommended that custom {@link ValidationKey} implementations are singletons.
 */
public interface ValidationKey {

    // User Entities
    // USER_PROFILE
    // USER_REGISTRATION

    /**
     * Collection of User specific validation keys.
     */
    interface User {

        // User Attributes
        KeycloakValidationKey USERNAME = new KeycloakValidationKey("user.username");
        KeycloakValidationKey EMAIL = new KeycloakValidationKey("user.email");
        KeycloakValidationKey FIRSTNAME = new KeycloakValidationKey("user.firstName");
        KeycloakValidationKey LASTNAME = new KeycloakValidationKey("user.lastName");

        List<KeycloakValidationKey> ALL_KEYS = Collections.unmodifiableList(Arrays.asList(USERNAME, EMAIL, FIRSTNAME, LASTNAME));
    }

    // TODO add additional supported attributes

    /**
     * The name of the {@link ValidationKey}
     *
     * @return
     */
    String name();

    /**
     * Create a new {@link ValidationKey}.
     * <p>
     * Note that this is only for internal user and for creation of custom {@link ValidationKey ValidationKeys}.
     *
     * @param name
     * @return
     */
    static CustomValidationKey newCustomKey(String name) {
        return new CustomValidationKey(name);
    }

    /**
     * Looks for an existing {@link ValidationKey} instance with the given name.
     *
     * @param name
     * @return
     */
    static ValidationKey lookup(String name) {

        for (ValidationKey key : User.ALL_KEYS) {
            if (key.name().equals(name)) {
                return key;
            }
        }

        // todo check other validation keys

        return null;
    }

    /**
     * Keycloak internal {@link ValidationKey}.
     * <p>
     * This type should only be used for Keycloak Internal {@link ValidationKey} implementations.
     * Users who want to define custom ValidationKeys should use the CustomValidationKey.
     */
    final class KeycloakValidationKey extends AbstractValidationKey {

        public KeycloakValidationKey(String name) {
            super(name);
        }
    }

    /**
     * Custom {@link ValidationKey}.
     * <p>
     * This is meant for users who want to define custom ValidationKeys.
     */
    final class CustomValidationKey extends AbstractValidationKey {

        public CustomValidationKey(String name) {
            super(name);
        }
    }

    /**
     * This type should be used for custom {@link ValidationKey} implementations.
     */
    abstract class AbstractValidationKey implements ValidationKey {

        private final String name;

        public AbstractValidationKey(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomValidationKey)) return false;
            AbstractValidationKey that = (AbstractValidationKey) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "Dynamic{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}
