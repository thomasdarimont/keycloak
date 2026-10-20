package org.keycloak.scim.provisioning.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import org.keycloak.scim.resource.group.Group;
import org.keycloak.scim.resource.group.Member;

/** Builds a SCIM {@code Group} resource (RFC 7643 §4.2). */
public final class GroupResourceBuilder {
    private GroupResourceBuilder() {}

    /**
     * @param displayName required
     * @param externalId may be {@code null}
     * @param memberIds the destination's resource ids of the members; sorted and de-duplicated
     */
    public static Group build(String displayName, String externalId, Collection<String> memberIds) {
        Group group = new Group();
        group.setDisplayName(displayName);
        group.setExternalId(externalId);
        for (String id : new TreeSet<>(memberIds)) {
            Member member = new Member();
            member.setValue(id);
            group.addMember(member);
        }
        if (group.getMembers() == null) {
            group.setMembers(new ArrayList<>());
        }
        return group;
    }
}
