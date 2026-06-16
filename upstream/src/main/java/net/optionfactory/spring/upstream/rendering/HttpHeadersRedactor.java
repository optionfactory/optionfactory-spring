package net.optionfactory.spring.upstream.rendering;

import java.util.Map;
import org.springframework.http.HttpHeaders;

public class HttpHeadersRedactor {

    private final Map<String, String> headerRedactions;

    public HttpHeadersRedactor(Map<String, String> headerRedactions) {
        this.headerRedactions = headerRedactions;
    }

    public HttpHeaders redact(HttpHeaders source) {
        if (source == null || source.isEmpty() || headerRedactions.isEmpty()) {
            return source;
        }
        final var result = new HttpHeaders(source);
        for (final var entry : headerRedactions.entrySet()) {
            if (!result.containsHeader(entry.getKey())) {
                continue;
            }
            result.set(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
