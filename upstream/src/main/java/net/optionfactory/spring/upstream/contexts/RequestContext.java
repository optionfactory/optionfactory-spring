package net.optionfactory.spring.upstream.contexts;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record RequestContext(
        long id,
        Instant at,
        HttpMethod method,
        URI uri,
        HttpHeaders headers,
        byte[] body) {

    public RequestContext withUri(URI uri) {
        return new RequestContext(id, at, method, uri, headers, body);
    }
}
