package net.optionfactory.spring.upstream.contexts;

import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record ResponseContext(
        Instant at,
        HttpStatusCode status,
        String statusText,
        HttpHeaders headers,
        byte[] body) {

}
