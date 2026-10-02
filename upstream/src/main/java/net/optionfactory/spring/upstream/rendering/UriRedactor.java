package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.util.Map;

/// Redacts the query parameters of a request uri before it is logged.
///
/// Each configured parameter present in the query, matched by its exact name, has all of its
/// values replaced by a single redacted value, where the parameter first appears; names are
/// matched still encoded. A uri that needs no redaction is returned as it is; in a redacted one
/// everything but the redacted values is kept as it is, escapes included, and the replacement is
/// encoded as a query parameter value.
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
        final var rawQuery = source.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return source;
        }
        final var redactedQuery = FormUrlencodedRedactor.redactQuery(rawQuery, paramsRedactions);
        if (redactedQuery.equals(rawQuery)) {
            return source;
        }
        final var uri = source.toString();
        final var fragment = source.getRawFragment();
        return URI.create(uri.substring(0, uri.indexOf('?') + 1) + redactedQuery + (fragment == null ? "" : "#" + fragment));
    }
}