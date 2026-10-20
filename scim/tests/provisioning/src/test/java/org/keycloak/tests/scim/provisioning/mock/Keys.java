package org.keycloak.tests.scim.provisioning.mock;

import java.util.UUID;

/**
 * Unique mock app keys. The mock picks its quirks from the key's prefix ({@code slack-}, {@code github-},
 * {@code zoom-}, {@code notion-}, {@code atlassian-}); every test gets its own app so tests never see each other's
 * accounts.
 */
public final class Keys {

    private Keys() {
    }

    /** {@code <family>-<random>}, e.g. {@code atlassian-3f9c0a1b2d}. */
    public static String unique(String family) {
        return family + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
