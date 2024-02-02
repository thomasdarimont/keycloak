package keycloak.utils.kc

import future.keywords.if
import future.keywords.in

isRealm(realmName) := result if {
	result := input.resource.realm == realmName
}

isClient(clientId) := result if {
	result := input.resource.clientId == clientId
}

hasRealmRole(roleName) := result if {
	result := roleName in input.subject.realmRoles
}

hasCurrentClientRole(roleName) := result if {
	client_role := concat(":", [input.resource.clientId, roleName])
	result := client_role in input.subject.clientRoles
}

hasClientRole(clientId, roleName) := result if {
	client_role := concat(":", [clientId, roleName])
	result := client_role in input.subject.clientRoles
}

hasUserAttribute(attribute) if {
	input.subject.attributes[attribute]
}

hasUserAttributeValue(attribute, value) if {
	input.subject.attributes[attribute] == value
}
