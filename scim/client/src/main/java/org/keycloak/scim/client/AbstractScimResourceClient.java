package org.keycloak.scim.client;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.scim.protocol.request.PatchRequest;
import org.keycloak.scim.protocol.request.SearchRequest;
import org.keycloak.scim.protocol.response.ErrorResponse;
import org.keycloak.scim.protocol.response.ListResponse;
import org.keycloak.scim.resource.ResourceTypeRepresentation;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;

import static java.util.Objects.requireNonNull;


public abstract class AbstractScimResourceClient<R extends ResourceTypeRepresentation> implements AutoCloseable {

    protected final ScimClient client;
    private final Class<R> resourceTypeClass;

    public AbstractScimResourceClient(ScimClient client, Class<R> resourceType) {
        this.client = client;
        this.resourceTypeClass = resourceType;
    }

    public R create(R resource) {
        requireNonNull(resource, "SCIM resource must not be null");
        return client.execute(client.doPost(resourceTypeClass).json(resource), resourceTypeClass);
    }

    public R update(R resource) {
        return update(resource.getId(), resource);
    }

    public R update(String id, R resource) {
        requireNonNull(resource, "SCIM resource must not be null");
        return client.execute(client.doPut(resourceTypeClass, id)
                .json(resource), resourceTypeClass);
    }

    public void delete(String id) {
        requireNonNull(id, "SCIM resource ID must not be null");
        client.execute(client.doDelete(resourceTypeClass, id));
    }

    public R get(String id) {
        return get(id, null, null);
    }

    public R get(String id, List<String> attributes) {
        return get(id, attributes, null);
    }

    public R get(String id, List<String> attributes, List<String> excludedAttributes) {
        requireNonNull(id, "SCIM resource ID must not be null");

        try {
            SimpleHttpRequest request = doGet("/" + id);

            Map<String, String> params = new HashMap<>();
            if (attributes != null && !attributes.isEmpty()) {
                params.put("attributes", String.join(",", attributes));
            }
            if (excludedAttributes != null && !excludedAttributes.isEmpty()) {
                params.put("excludedAttributes", String.join(",", excludedAttributes));
            }
            if (!params.isEmpty()) {
                request = request.params(params);
            }

            return client.execute(request, resourceTypeClass);
        } catch (ScimClientException scime) {
            ErrorResponse error = scime.getError();

            if (error != null) {
                if (HttpStatus.SC_NOT_FOUND == error.getStatusInt()) {
                    return null;
                }
            }

            throw scime;
        }
    }

    public void patch(String id, PatchRequest request) {
        requireNonNull(request, "request must not be null");
        client.execute(client.doPatch(resourceTypeClass, id).json(request));
    }

    @SuppressWarnings("unchecked")
    protected ListResponse<R> doFilter(ResourceFilter filter) {
        return doFilter(filter, null, null);
    }

    @SuppressWarnings("unchecked")
    protected ListResponse<R> doFilter(ResourceFilter filter, List<String> attributes, List<String> excludedAttributes) {
        SimpleHttpRequest request = doGet("");

        Map<String, String> params = new HashMap<>();
        String query = filter.build();
        if (!query.isEmpty()) {
            params.put("filter", query);
        }
        if (attributes != null && !attributes.isEmpty()) {
            params.put("attributes", String.join(",", attributes));
        }
        if (excludedAttributes != null && !excludedAttributes.isEmpty()) {
            params.put("excludedAttributes", String.join(",", excludedAttributes));
        }
        if (!params.isEmpty()) {
            request = request.params(params);
        }

        return client.execute(request, ListResponse.class);
    }

    /**
     * One page of {@code GET /<resource type>}, optionally filtered. Unlike {@link #doFilter}, every
     * element of {@code Resources} is read as this client's resource type, so servers that leave out
     * {@code schemas} on list elements are understood too.
     *
     * @param filterExpression SCIM filter expression, or {@code null} for no filter
     * @param startIndex 1-based index of the first result, or {@code null} for the server's default
     * @param count maximum number of results, or {@code null} for the server's default
     * @return the page
     */
    public ListResponse<R> list(String filterExpression, Integer startIndex, Integer count) {
        SimpleHttpRequest request = doGet("");
        Map<String, String> params = new HashMap<>();
        if (filterExpression != null) {
            params.put("filter", filterExpression);
        }
        if (startIndex != null) {
            params.put("startIndex", startIndex.toString());
        }
        if (count != null) {
            params.put("count", count.toString());
        }
        if (!params.isEmpty()) {
            request = request.params(params);
        }
        try (SimpleHttpResponse response = client.execute(request)) {
            JsonNode body = response.asJson();
            if (body == null || !body.isObject()) {
                throw new ScimClientException("List response is not a JSON object", (String) null);
            }
            ListResponse<R> page = new ListResponse<>();
            List<R> resources = new ArrayList<>();
            JsonNode elements = body.get("Resources");
            if (elements != null && elements.isArray()) {
                for (JsonNode element : elements) {
                    resources.add(JsonSerialization.mapper.treeToValue(element, resourceTypeClass));
                }
            } else if (elements != null && !elements.isNull()) {
                throw new ScimClientException("Resources is not an array", (String) null);
            }
            page.setResources(resources);
            if (body.hasNonNull("totalResults")) {
                page.setTotalResults(body.get("totalResults").asInt());
            }
            if (body.hasNonNull("startIndex")) {
                page.setStartIndex(body.get("startIndex").asInt());
            }
            if (body.hasNonNull("itemsPerPage")) {
                page.setItemsPerPage(body.get("itemsPerPage").asInt());
            }
            return page;
        } catch (IOException e) {
            throw new ScimClientException("Error reading list response", e);
        }
    }

    protected SimpleHttpRequest doGet(String path) {
        return client.doGet(resourceTypeClass, path);
    }

    /**
     * Search for resources using the POST /.search endpoint.
     * This is useful for complex filters that may exceed URL length limits.
     *
     * @param filterExpression SCIM filter expression (e.g., "userName eq \"john\"")
     * @param startIndex      optional index of the first result to return (for pagination)
     *                        if null, the server will use its default value (usually 1)
     * @param count           optional maximum number of results to return (for pagination)
     *                        if null, the server will use its default value
     * @return list response containing matching resources
     */
    @SuppressWarnings("unchecked")
    public ListResponse<R> doPost(String filterExpression, Integer startIndex, Integer count) {
        SearchRequest searchRequest = SearchRequest.builder()
                .withFilter(filterExpression)
                .withStartIndex(startIndex)
                .withCount(count).build();
        return client.execute(client.doPost(resourceTypeClass, "/.search").json(searchRequest), ListResponse.class);
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
