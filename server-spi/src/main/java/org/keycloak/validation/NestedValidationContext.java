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

import org.keycloak.models.KeycloakSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link ValidationContext} for nested {@link Validation Validation's}
 */
public class NestedValidationContext extends ValidationContext {

    private final KeycloakSession session;

    /**
     * Holds the {@link List} of {@link ValidationProblem ValidationProblem's}
     */
    private final List<ValidationProblem> problems;

    public NestedValidationContext(ValidationContext parent, KeycloakSession session) {
        super(parent.getRealm(), parent.getContextKey(), parent.getAttributes());
        this.session = session;
        this.problems = new ArrayList<>();
    }

    public KeycloakSession getSession() {
        return session;
    }

    public List<ValidationProblem> getProblems() {
        return problems;
    }

    /**
     * @param problem
     */
    public void addProblem(ValidationProblem problem) {
        Objects.requireNonNull(problem, "problem");
        getProblems().add(problem);
    }

    public void addError(ValidationKey key, String message) {
        addProblem(ValidationProblem.error(key, message));
    }

    public void addWarning(ValidationKey key, String message) {
        addProblem(ValidationProblem.warning(key, message));
    }
}
