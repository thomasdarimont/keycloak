package org.keycloak.scim.provisioning.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.databind.JsonNode;

/** SHA-256 over canonical JSON: the payload hash stored on a link row. */
public final class Hashing {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashing() {}

    /** Lower-case hex SHA-256 of the bytes (64 characters). */
    public static String sha256Hex(byte[] bytes) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
        byte[] h = md.digest(bytes);
        char[] out = new char[h.length * 2];
        for (int i = 0; i < h.length; i++) {
            out[2 * i] = HEX[(h[i] >> 4) & 0xF];
            out[2 * i + 1] = HEX[h[i] & 0xF];
        }
        return new String(out);
    }

    /** The hash of a resource: SHA-256 of its RFC 8785 canonical form. */
    public static String hash(JsonNode resource) {
        return sha256Hex(CanonicalJson.canonicalize(resource));
    }
}
