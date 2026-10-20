package org.keycloak.tests.scim.provisioning.delivery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * Wraps a real HTTP client: records every request (method, URI, headers, body) and lets a test answer some of them
 * itself through {@link #interceptor}.
 */
@SuppressWarnings("deprecation")
public final class RecordingHttpClient extends CloseableHttpClient {

    /** One request as sent. Header names are as written; several values of one header are joined with ", ". */
    public record Request(String method, URI uri, Map<String, String> headers, byte[] body) {

        public String header(String name) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getKey().equalsIgnoreCase(name)) {
                    return h.getValue();
                }
            }
            return null;
        }

        public String bodyText() {
            return body == null ? null : new String(body, StandardCharsets.UTF_8);
        }
    }

    private final CloseableHttpClient delegate;

    public final List<Request> requests = new CopyOnWriteArrayList<>();

    /** Returns a response to short-circuit the request, or null to pass it on to the real client. */
    public volatile Function<Request, HttpResponse> interceptor = r -> null;

    public RecordingHttpClient(CloseableHttpClient delegate) {
        this.delegate = delegate;
    }

    /** A canned response with a JSON body. */
    public static HttpResponse json(int status, String body) {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, status, null);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(bytes));
        entity.setContentLength(bytes.length);
        entity.setContentType("application/scim+json");
        response.setEntity(entity);
        response.setHeader("Content-Type", "application/scim+json");
        return response;
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {
        byte[] body = null;
        if (request instanceof HttpEntityEnclosingRequest enclosing && enclosing.getEntity() != null) {
            BufferedHttpEntity buffered = new BufferedHttpEntity(enclosing.getEntity());
            enclosing.setEntity(buffered);
            body = EntityUtils.toByteArray(buffered);
        }
        URI uri = request instanceof HttpUriRequest u ? u.getURI() : URI.create(request.getRequestLine().getUri());
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header h : request.getAllHeaders()) {
            headers.merge(h.getName(), h.getValue(), (a, b) -> a + ", " + b);
        }
        Request recorded = new Request(request.getRequestLine().getMethod(), uri, headers, body);
        requests.add(recorded);

        HttpResponse canned = interceptor.apply(recorded);
        if (canned != null) {
            return new Canned(canned);
        }
        return delegate.execute(target, request, context);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public HttpParams getParams() {
        return delegate.getParams();
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return delegate.getConnectionManager();
    }

    /** A canned response as the closeable response the client API returns. */
    private static final class Canned extends BasicHttpResponse implements CloseableHttpResponse {
        Canned(HttpResponse r) {
            super(r.getStatusLine());
            setHeaders(r.getAllHeaders());
            setEntity(r.getEntity());
        }

        @Override
        public void close() {
            EntityUtils.consumeQuietly(getEntity());
        }
    }
}
