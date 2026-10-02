package net.optionfactory.spring.upstream.contexts;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/// A request about to be sent, as seen by interceptors and expressions (`#request`).
///
/// The headers and attributes are those of the actual request: changing them changes what is sent.
/// The record itself is immutable, and an interceptor changes the uri by passing on a copy made with
/// [#withUri].
///
/// @param at when the request entered the interceptors, according to the client clock
/// @param method the request method
/// @param uri the request uri
/// @param headers the request headers
/// @param attributes the request attributes
/// @param body the serialized request body, empty when there is none
public record RequestContext(
        Instant at,
        HttpMethod method,
        URI uri,
        HttpHeaders headers,
        Map<String, Object> attributes,
        byte[] body) {

    /// @param uri the new uri
    /// @return a copy of this request with another uri, sharing headers, attributes and body
    public RequestContext withUri(URI uri) {
        return new RequestContext(at, method, uri, headers, attributes, body);
    }
}
