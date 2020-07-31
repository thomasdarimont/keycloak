package org.keycloak.validation;

public class ValidationProblem {

    public enum Severity {
        WARNING, ERROR
    }

    private String key;

    private String message;

    private Severity severity;

    public ValidationProblem(String key, String message, Severity severity) {
        this.key = key;
        this.message = message;
        this.severity = severity;
    }

    public String getKey() {
        return key;
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    public static ValidationProblem warning(String key, String message) {
        return new ValidationProblem(key, message, Severity.WARNING);
    }

    public static ValidationProblem error(String key, String message) {
        return new ValidationProblem(key, message, Severity.ERROR);
    }

    @Override
    public String toString() {
        return "ValidationProblem{" +
                "key='" + key + '\'' +
                ", message='" + message + '\'' +
                ", severity=" + severity +
                '}';
    }
}
