package keycloak.realms.opademo2.access

import future.keywords.if
import future.keywords.in

import data.keycloak.utils.kc.isClient
import data.keycloak.utils.kc.hasRealmRole

# default rule "allow"
default allow := {"allow":false, "message":"access-denied"}

# rule "allow" for client-id:account-console
allow := {"allow":true, "message":"user can access"} if {
	isClient("account-console")
}