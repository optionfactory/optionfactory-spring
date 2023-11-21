package net.optionfactory.spring.upstream.faults;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

public record UpstreamFaultEvent(
        String boot,
        String upstream,
        Object principal,
        String endpoint,
        Method method,
        Object[] arguments,
        long requestId,
        Instant requestedAt,
        URI requestUri,
        HttpMethod requestMethod,
        HttpHeaders requestHeaders,
        byte[] requestBody,
        Instant handledAt,
        HttpHeaders responseHeaders,
        HttpStatusCode responseStatus,
        byte[] responseBody,
        Exception exception) {

}
