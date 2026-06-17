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

public class FormUrlencodedRedactor {

    private final Map<String, String> paramsRedactions;

    public FormUrlencodedRedactor(Map<String, String> paramsRedactions) {
        this.paramsRedactions = paramsRedactions;
    }

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
