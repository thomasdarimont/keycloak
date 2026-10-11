package org.keycloak.scim.provisioning.model;

/**
 * A (realm, destination) pair that has rows in the outbox. The sweeper compares these with the
 * destinations that still exist as components, and removes the rows of those that do not.
 */
public record DestinationRef(String realmId, String destinationId) {}
