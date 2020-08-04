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
