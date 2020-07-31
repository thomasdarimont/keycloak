package org.keycloak.validation.validator;

import org.keycloak.validation.ValidationContext;
import org.keycloak.validation.ValidationProblem;

import java.util.List;

public interface ValueValidation<V> {

    boolean validate(ValidationContext context, String key, V value, List<ValidationProblem> problems);
}
