package org.keycloak.scim.provisioning.component;

import java.util.Optional;

import org.keycloak.models.KeycloakSession;
import org.keycloak.vault.VaultStringSecret;

/**
 * Resolves a destination token at the point of use: a {@code
 * ${vault.x}} reference goes through Keycloak's vault, a literal passes through. The result is
 * never logged and never stored.
 */
public final class Tokens {
    private Tokens() {}

    /** Empty when a vault reference cannot be resolved. */
    public static Optional<String> resolve(KeycloakSession session, String ref) {
        if (ref == null || ref.isEmpty()) return Optional.of("");
        try (VaultStringSecret secret = session.vault().getStringSecret(ref)) {
            Optional<String> v = secret.get();
            if (v.isPresent()) return Optional.of(v.get());
        }
        return ref.startsWith("${vault.") ? Optional.empty() : Optional.of(ref);
    }
}
