package keycloak.realms.opademo.access

import future.keywords.if
import future.keywords.in

import data.keycloak.utils.kc.isClient
import data.keycloak.utils.kc.hasRealmRole

# default rule "allow"
default allow := {"allow":false, "message":"access-denied"}

# rule "allow" for client-id:account-console with realm-role:user
allow := {"allow":true, "message":"user can access"} if {
	isClient("account-console")
	hasRealmRole("user")
}

# rule "allow" for client-id:keycloak.org
allow := {"allow":true, "message":"user can access"} if {
	isClient("keycloak.org")
}