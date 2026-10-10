package org.keycloak.scim.provisioning.delivery;

import java.util.Locale;

/** What leaving scope does to the account in the destination. */
public enum DeprovisionMode {
    /** {@code PATCH active=false}, falling back to a read-modify-write {@code PUT}. The default. */
    DEACTIVATE,
    /** {@code DELETE}. Opt-in per destination. */
    DELETE;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code "deactivate"} or {@code "delete"}; anything else, including null, is deactivate. */
    public static DeprovisionMode parse(String s) {
        return s != null && s.strip().equalsIgnoreCase("delete") ? DELETE : DEACTIVATE;
    }
}
