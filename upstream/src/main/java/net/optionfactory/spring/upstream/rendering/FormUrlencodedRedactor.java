package net.optionfactory.spring.upstream.rendering;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriUtils;

/// Redacts the parameters of an `application/x-www-form-urlencoded` body before it is logged.
///
/// Each configured parameter present in the body has all of its values replaced by a single
/// redacted value, where the parameter first appears; parameter names are matched exactly, case
/// included, still encoded. The rest of the body is rendered as it is, escapes included, line
/// breaks removed; the replacement is encoded as a query parameter value.
public class FormUrlencodedRedactor {

    private final Map<String, String> paramsRedactions;

    /// @param paramsRedactions the replacement value of each parameter to redact, by name
    public FormUrlencodedRedactor(Map<String, String> paramsRedactions) {
        this.paramsRedactions = paramsRedactions;
    }

    /// @param source the body, read as UTF-8
    /// @return the redacted body, or an empty string for a blank body
    /// @throws java.io.UncheckedIOException when the body cannot be read
    public String redact(InputStreamSource source) {
        try (final var is = source.getInputStream()) {
            final var rawFormBody = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            if (rawFormBody == null || rawFormBody.trim().isEmpty()) {
                return "";
            }
            return redactQuery(rawFormBody.replaceAll("[\r\n]+", ""), paramsRedactions);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /// Redacts the parameters of a raw (encoded) query string, see [FormUrlencodedRedactor].
    ///
    /// @param rawQuery the encoded query string
    /// @param paramsRedactions the replacement value of each parameter to redact, by name
    /// @return the query string, redacted
    static String redactQuery(String rawQuery, Map<String, String> paramsRedactions) {
        final var redacted = new HashSet<String>();
        final var pieces = new ArrayList<String>();
        for (final var piece : rawQuery.split("&", -1)) {
            final var eq = piece.indexOf('=');
            final var name = eq == -1 ? piece : piece.substring(0, eq);
            if (!paramsRedactions.containsKey(name)) {
                pieces.add(piece);
                continue;
            }
            if (redacted.add(name)) {
                pieces.add(name + "=" + UriUtils.encodeQueryParam(paramsRedactions.get(name), StandardCharsets.UTF_8));
            }
        }
        return String.join("&", pieces);
    }
}
