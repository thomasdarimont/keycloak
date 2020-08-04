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

/**
 * Denotes a validatable property, e.g. Realm Attributes, User Properties, Client Properties, etc.
 * <p>
 * Users can create custom {@link ValidationKey ValidationKey's} by implementing this interface.
 * It is recommended that custom {@link ValidationKey} implementations are singletons, hence enums are a good choice.
 *
 * An example for a custom ValidationKey enum could look like this:
 * <pre>{@code
 *  enum MyCustomUserValidationKey implements ValidationKey {
 *     USER_CUSTOM_PHONENUMBER,
 *     USER_CUSTOM_ADDRESS;
 *  }}
 * </pre>
 */
public interface ValidationKey {

    ValidationKey USER_USERNAME = UserValidationKey.USER_USERNAME;
    ValidationKey USER_EMAIL = UserValidationKey.USER_EMAIL;
    ValidationKey USER_FIRSTNAME = UserValidationKey.USER_FIRSTNAME;
    ValidationKey USER_LASTNAME = UserValidationKey.USER_LASTNAME;

    enum UserValidationKey implements ValidationKey {

        // User Entities
        // USER_PROFILE
        // USER_REGISTRATION

        // User Attributes
        USER_USERNAME,
        USER_EMAIL,
        USER_FIRSTNAME,
        USER_LASTNAME,

        // TODO add default supported attributes
    }

    // TODO define more validatable types with properties / attributes
}
