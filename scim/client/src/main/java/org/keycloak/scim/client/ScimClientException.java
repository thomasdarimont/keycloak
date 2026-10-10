package org.keycloak.scim.client;

import org.keycloak.scim.protocol.response.ErrorResponse;

public class ScimClientException extends RuntimeException {

    private final String response;
    private ErrorResponse error;
    private int status;
    private String retryAfter;

    public ScimClientException(String message, Throwable cause, String response) {
        super(message, cause);
        this.response = response;
    }

    public ScimClientException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public ScimClientException(String message, String response) {
        this(message, null, response);
    }

    public <T> ScimClientException(String message, ErrorResponse error) {
        this(message, null, null);
        this.error = error;
    }

    /**
     * An error response.
     *
     * @param status the HTTP status
     * @param retryAfter the {@code Retry-After} header, if the server sent one
     */
    public ScimClientException(String message, ErrorResponse error, int status, String retryAfter) {
        this(message, error);
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public String getResponse() {
        return response;
    }

    public ErrorResponse getError() {
        return error;
    }

    /**
     * @return the HTTP status of an error response, or 0 when no response was received
     */
    public int getStatus() {
        if (status == 0 && error != null) {
            try {
                return error.getStatusInt();
            } catch (RuntimeException e) {
                return 0;
            }
        }
        return status;
    }

    /**
     * @return the {@code Retry-After} header of an error response, or {@code null}
     */
    public String getRetryAfter() {
        return retryAfter;
    }
}
