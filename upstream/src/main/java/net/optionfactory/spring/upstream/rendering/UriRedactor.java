package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;


/// Redacts the query parameters of a request uri before it is logged.
///
/// Each configured parameter present in the query, matched by its exact name, has all of its
/// values replaced by a single redacted value, moved to the end of the query. A uri that needs no
/// redaction is returned as it is;
/// a redacted one is re-encoded, and the re-encoding also applies to what was already encoded: a
/// `%20` in the original query is rendered as `%2520`.
public class UriRedactor {

    private final Map<String, String> paramsRedactions;

    /// @param paramsRedactions the replacement value of each parameter to redact, by name
    public UriRedactor(Map<String, String> paramsRedactions) {
        this.paramsRedactions = paramsRedactions;
    }

    /// @param source the uri to redact, possibly `null`
    /// @return the source itself when nothing is redacted, a new uri otherwise
    public URI redact(URI source) {
        if (source == null || paramsRedactions == null || paramsRedactions.isEmpty()) {
            return source;
        }
        final var currentQueryParams = UriComponentsBuilder.fromUri(source).build().getQueryParams();
        if (currentQueryParams.isEmpty()) {
            return source;
        }
        final var builder = UriComponentsBuilder.fromUri(source);
        boolean mutated = false;

        for (final var entry : paramsRedactions.entrySet()) {
            if (currentQueryParams.containsKey(entry.getKey())) {
                builder.replaceQueryParam(entry.getKey(), entry.getValue());
                mutated = true;
            }
        }
        return mutated ? builder.build().toUri() : source;
    }
}