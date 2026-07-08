package org.keycloak.util;


import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;

/**
 * Parser, which is able to create specific subtype of {@link AuthorizationDetailsJSONRepresentation} in performant way
 */
public interface AuthorizationDetailsParser<T extends AuthorizationDetailsJSONRepresentation> {

    T asSubtype(AuthorizationDetailsJSONRepresentation authzDetail, Class<T> clazz);

    /**
     * Method is not supposed to be called directly. Rather please make sure to use {@link #registerParser(String, AuthorizationDetailsParser)} and
     * then use {@link #asSubtype(AuthorizationDetailsJSONRepresentation, Class)} to call directly from the application
     *
     * @param authzDetail Authorization detail object to cast
     * @param clazz Subtype of {@link AuthorizationDetailsJSONRepresentation}, which will be returned by calling this method
     * @return given authzDetail passed in <em>authzDetail</em> parameter cast to the class specified by clazz parameter as long as parser corresponding to the type
     * returned by {@link AuthorizationDetailsJSONRepresentation#getType} is able to parse this authorizationDetails and convert it to that subtype
     */
    T parseToSubtype(AuthorizationDetailsJSONRepresentation authzDetail, Class<T> clazz);
}
