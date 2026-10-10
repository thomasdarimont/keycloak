package org.keycloak.scim.provisioning.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.keycloak.scim.protocol.request.PatchRequest;
import org.keycloak.scim.protocol.request.PatchRequest.PatchOperation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** The PATCH requests this provider sends (RFC 7644 §3.5.2). */
public final class PatchOps {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private PatchOps() {}

    /** {@code replace active=false}: deactivation without touching anything else. */
    public static PatchRequest deactivate() {
        return request(replace("active", JSON.booleanNode(false)));
    }

    /** {@code replace members}: the whole member list, sorted. */
    public static PatchRequest replaceMembers(Collection<String> memberIds) {
        return request(replace("members", members(memberIds)));
    }

    /** {@code replace displayName}. */
    public static PatchRequest replaceDisplayName(String displayName) {
        return request(replace("displayName", JSON.textNode(displayName)));
    }

    /** Both group operations in one request. */
    public static PatchRequest replaceGroup(String displayName, Collection<String> memberIds) {
        return request(replace("displayName", JSON.textNode(displayName)), replace("members", members(memberIds)));
    }

    private static ArrayNode members(Collection<String> ids) {
        ArrayNode a = JSON.arrayNode();
        for (String id : new TreeSet<>(ids)) a.addObject().put("value", id);
        return a;
    }

    private static PatchOperation replace(String path, JsonNode value) {
        PatchOperation op = new PatchOperation();
        op.setOp("replace");
        op.setPath(path);
        op.setValue(value);
        return op;
    }

    private static PatchRequest request(PatchOperation... operations) {
        List<PatchOperation> ops = new ArrayList<>(List.of(operations));
        return new PatchRequest(ops);
    }
}
