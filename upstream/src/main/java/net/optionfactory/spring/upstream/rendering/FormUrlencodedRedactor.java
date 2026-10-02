package net.optionfactory.spring.upstream.rendering;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;

/// Redacts the parameters of an `application/x-www-form-urlencoded` body before it is logged.
///
/// Each configured parameter present in the body has all of its values replaced by a single
/// redacted value; parameter names are matched exactly, case included. The body is then
/// re-encoded, and the re-encoding also applies to what was already encoded: a `%20` in the
/// original body is rendered as `%2520`.
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
            final var parsedParams = UriComponentsBuilder.newInstance()
                    .query(rawFormBody)
                    .build()
                    .getQueryParams();

            final var mutableParams = new LinkedMultiValueMap<>(parsedParams);

            for (var rule : paramsRedactions.entrySet()) {
                final var targetKey = rule.getKey();
                final var redactedValue = rule.getValue();
                if (mutableParams.containsKey(targetKey)) {
                    mutableParams.set(targetKey, redactedValue);
                }
            }
            final var redactedBody = UriComponentsBuilder.newInstance()
                    .queryParams(mutableParams)
                    .build()
                    .encode()
                    .getQuery();
            return redactedBody != null ? redactedBody : "";
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
