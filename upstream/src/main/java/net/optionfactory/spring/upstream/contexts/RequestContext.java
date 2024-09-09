package net.optionfactory.spring.upstream.contexts;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record RequestContext(
        Instant at,
        HttpMethod method,
        URI uri,
        HttpHeaders headers,
        byte[] body) {

    public RequestContext withUri(URI uri) {
        return new RequestContext(at, method, uri, headers, body);
    }
}
