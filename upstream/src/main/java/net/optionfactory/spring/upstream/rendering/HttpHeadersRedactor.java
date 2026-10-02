package net.optionfactory.spring.upstream.rendering;

import java.util.Map;
import org.springframework.http.HttpHeaders;

/// Redacts http headers before they are logged.
///
/// Each configured header present, matched case-insensitively, has all of its values replaced by
/// a single redacted value.
///
/// The redacted headers are a copy: the source headers, typically those of a request still to be
/// sent, are never changed.
public class HttpHeadersRedactor {

    private final Map<String, String> headerRedactions;

    /// @param headerRedactions the replacement value of each header to redact, by name
    public HttpHeadersRedactor(Map<String, String> headerRedactions) {
        this.headerRedactions = headerRedactions;
    }

    /// @param source the headers to redact, possibly `null`
    /// @return the source itself when it is `null` or empty or there is nothing to redact, a
    /// redacted copy otherwise
    public HttpHeaders redact(HttpHeaders source) {
        if (source == null || source.isEmpty() || headerRedactions.isEmpty()) {
            return source;
        }
        final var result = HttpHeaders.copyOf(source);
        for (final var entry : headerRedactions.entrySet()) {
            if (!result.containsHeader(entry.getKey())) {
                continue;
            }
            result.set(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
