package net.optionfactory.spring.upstream.contexts;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record RequestContext(
        Instant at,
        HttpMethod method,
        URI uri,
        HttpHeaders headers,
        Map<String, Object> attributes,
        byte[] body) {

    public RequestContext withUri(URI uri) {
        return new RequestContext(at, method, uri, headers, attributes, body);
    }
}
