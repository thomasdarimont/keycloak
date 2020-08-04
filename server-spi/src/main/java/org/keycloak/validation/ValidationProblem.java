package org.keycloak.validation;

/**
 * Denotes a problem that occurred during validatoin.
 */
public class ValidationProblem {

    public enum Severity {
        /**
         * Warning: the validation was performed but created a warning. The value might still be considered valid.
         */
        WARNING,

        /**
         * Error: the validation failed
         */
        ERROR
    }

    /**
     * Holds the validation key.
     */
    private final ValidationKey key;

    /**
     * Holds the i18n validation message.
     */
    private final String message;

    /**
     * Holds the severity of the validation problem.
     */
    private final Severity severity;

    public ValidationProblem(ValidationKey key, String message, Severity severity) {
        this.key = key;
        this.message = message;
        this.severity = severity;
    }

    public ValidationKey getKey() {
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

    public static ValidationProblem warning(ValidationKey key, String message) {
        return new ValidationProblem(key, message, Severity.WARNING);
    }

    public static ValidationProblem error(ValidationKey key, String message) {
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
