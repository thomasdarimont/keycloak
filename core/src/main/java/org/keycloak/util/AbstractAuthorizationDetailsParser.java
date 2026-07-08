package org.keycloak.util;

import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;

/**
 * Base class for custom AuthorizationDetails parser implementations.
 *
 * @param <T>
 */
public abstract class AbstractAuthorizationDetailsParser<T extends AuthorizationDetailsJSONRepresentation> implements AuthorizationDetailsParser<T>{

    /**
     * Converts the given AuthorizationDetailsJSONRepresentation into the given subtype.
     *
     * @param authzDetail
     * @param clazz
     * @return
     */
    @Override
    public abstract T asSubtype(AuthorizationDetailsJSONRepresentation authzDetail, Class<T> clazz);

    @Override
    public T parseToSubtype(AuthorizationDetailsJSONRepresentation authzDetail, Class<T> clazz) {
        checkAuthorizationDetails(authzDetail);
        return asSubtype(authzDetail, clazz);
    }

    /**
     * Checks the authorization details for valid values before converting them into a more specific AuthorizationDetailsJSONRepresentation.
     * @param authzDetail
     */
    protected void checkAuthorizationDetails(AuthorizationDetailsJSONRepresentation authzDetail) {
        if (authzDetail.getType() == null) {
            throw new IllegalArgumentException("Used authzDetail entry does not have 'type' set. The used authzDetail entry was: " + authzDetail);
        }
    }
}
