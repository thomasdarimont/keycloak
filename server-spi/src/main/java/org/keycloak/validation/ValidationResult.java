package org.keycloak.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Denotes the result of a validation run.
 */
public class ValidationResult {

    public static ValidationResult OK = new ValidationResult(true, Collections.emptyList());

    /**
     * Tells whether the validation outcome is considered valid.
     */
    private final boolean valid;

    /**
     * Holds the {@link ValidationProblem} detected during the validation run.
     */
    private final List<ValidationProblem> problems;

    public ValidationResult(boolean valid, List<ValidationProblem> problems) {
        this.valid = valid;
        this.problems = new ArrayList<>(problems);
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

    public List<ValidationProblem> getWarnings() {
        return filter(getProblems().stream(), ValidationProblem::isWarning).collect(Collectors.toList());
    }

    public List<ValidationProblem> getWarnings(String key) {
        return filter(getProblems().stream(), ValidationProblem::isWarning, p -> p.getKey().equals(key)).collect(Collectors.toList());
    }

    public List<ValidationProblem> getErrors() {
        return filter(getProblems().stream(), ValidationProblem::isError).collect(Collectors.toList());
    }

    public List<ValidationProblem> getErrors(String key) {
        return filter(getProblems().stream(), ValidationProblem::isError, p -> p.getKey().equals(key)).collect(Collectors.toList());
    }

    @SafeVarargs
    private final Stream<ValidationProblem> filter(Stream<ValidationProblem> stream, Predicate<ValidationProblem>... preds) {
        Stream<ValidationProblem> filtered = stream;
        for (Predicate<ValidationProblem> pred : preds) {
            filtered = filtered.filter(pred);
        }
        return filtered;
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
