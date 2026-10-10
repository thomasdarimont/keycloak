package org.keycloak.scim.provisioning.client;

import java.util.List;

import org.keycloak.scim.resource.user.User;

/**
 * One page of users (RFC 7644 §3.4.2). {@code totalResults} may be absent or wrong on some
 * destinations; callers stop when {@code resources} is empty or shorter than requested.
 *
 * @param totalResults {@code null} when the destination did not say
 */
public record ListPage(List<User> resources, Integer totalResults, int startIndex) {
    public ListPage {
        resources = List.copyOf(resources);
    }
}
