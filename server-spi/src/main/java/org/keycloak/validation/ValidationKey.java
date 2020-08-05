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
        ValidationKey USERNAME = create("user.username", 1000.0);
        ValidationKey EMAIL = create("user.email", 1100.0);
        ValidationKey FIRSTNAME = create("user.firstName", 1200.0);
        ValidationKey LASTNAME = create("user.lastName", 1300.0);

        List<ValidationKey> ALL_KEYS = Collections.unmodifiableList(Arrays.asList(USERNAME, EMAIL, FIRSTNAME, LASTNAME));
    }

    // TODO add additional supported attributes

    /**
     * The name of the {@link ValidationKey}
     *
     * @return
     */
    String name();

    /**
     * Denotes the order in which the referenced validation should take place.
     *
     * @return
     */
    double order();

    /**
     * Create a new {@link ValidationKey}.
     * <p>
     * Note that this is only for internal user and for creation of custom {@link ValidationKey ValidationKeys}.
     *
     * @param name
     * @param order
     * @return
     */
    static ValidationKey create(String name, double order) {
        return new SimpleValidationKey(name, order);
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

    class SimpleValidationKey implements ValidationKey {

        private final String name;

        private final double order;

        public SimpleValidationKey(String name, double order) {
            this.name = name;
            this.order = order;
        }

        public String name() {
            return name;
        }

        public double order() {
            return order;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleValidationKey key = (SimpleValidationKey) o;
            return Double.compare(key.order, order) == 0 &&
                    Objects.equals(name, key.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, order);
        }

        @Override
        public String toString() {
            return "Dynamic{" +
                    "name='" + name + '\'' +
                    ", order=" + order +
                    '}';
        }
    }
}
