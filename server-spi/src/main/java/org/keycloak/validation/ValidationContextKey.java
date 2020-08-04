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
 * Denotes a dedicated ValidationContext in which certain {@link Validation} rules should be applied, e.g. during User Registration,
 * User Profile change, Client Registration, Realm Definition, Identity Provider Configuration, etc.
 * {@link Validation Validation's} can be associated with different ValidationContextKey.
 * <p>
 * Users can create custom {@link ValidationContextKey ValidationContextKey's} by implementing this interface.
 * It is recommended that custom {@link ValidationContextKey} implementations are singletons, hence enums are a good choice.
 */
public interface ValidationContextKey {

    ValidationContextKey USER_PROFILE_UPDATE = DefaultContext.USER_PROFILE_UPDATE;

    ValidationContextKey USER_REGISTRATION = DefaultContext.USER_REGISTRATION;

    enum DefaultContext implements ValidationContextKey {

        USER_PROFILE_UPDATE,

        USER_REGISTRATION;
    }
}
