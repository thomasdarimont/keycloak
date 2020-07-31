package org.keycloak.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ValidationResult {

    public static ValidationResult OK = new ValidationResult(true, Collections.emptyList());

    private final boolean valid;

    private final List<ValidationProblem> problems;

    public ValidationResult(boolean valid, List<ValidationProblem> problems) {
        this.valid = valid;
        this.problems = new ArrayList<>(problems);
    }

    public ValidationResult(ValidationResult first, ValidationResult second) {
        this.valid = first.isValid() && second.isValid();
        List<ValidationProblem> problems = new ArrayList<>();
        problems.addAll(first.getProblems());
        problems.addAll(second.getProblems());
        this.problems = problems;
    }

    public boolean isOk() {
        return this == OK;
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationProblem> getProblems() {
        return problems;
    }

    public ValidationResult add(ValidationProblem problem) {
        getProblems().add(problem);
        return this;
    }

    public boolean hasProblems() {
        return !getProblems().isEmpty();
    }

    public boolean hasErrors() {
        return getProblems().stream().anyMatch(ValidationProblem::isError);
    }

    public boolean hasWarnings() {
        return getProblems().stream().anyMatch(ValidationProblem::isWarning);
    }

    public List<ValidationProblem> getErrors() {
        return getProblems().stream().filter(ValidationProblem::isError).collect(Collectors.toList());
    }

    public List<ValidationProblem> getWarnings() {
        return getProblems().stream().filter(ValidationProblem::isWarning).collect(Collectors.toList());
    }

    public void onError(Consumer<ValidationResult> consumer) {
        consumer.accept(this);
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", problems=" + problems +
                '}';
    }
}
