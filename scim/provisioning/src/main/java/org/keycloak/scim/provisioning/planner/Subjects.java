package org.keycloak.scim.provisioning.planner;

/** Subject ids: {@code user:<keycloak id>} and {@code group:<keycloak id>}. */
public final class Subjects {
    public static final String USER = "user:";
    public static final String GROUP = "group:";

    private Subjects() {}

    public static String user(String id) {
        return USER + id;
    }

    public static String group(String id) {
        return GROUP + id;
    }

    public static boolean isUser(String subject) {
        return subject.startsWith(USER);
    }

    public static boolean isGroup(String subject) {
        return subject.startsWith(GROUP);
    }

    /** The Keycloak id inside a subject. */
    public static String id(String subject) {
        int c = subject.indexOf(':');
        return c < 0 ? subject : subject.substring(c + 1);
    }
}
