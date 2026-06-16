package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;


public class UriRedactor {

    private final Map<String, String> queryParamRedactions;

    public UriRedactor(Map<String, String> queryParamRedactions) {
        this.queryParamRedactions = queryParamRedactions;
    }

    public URI redact(URI source) {
        if (source == null || queryParamRedactions == null || queryParamRedactions.isEmpty()) {
            return source;
        }
        final var currentQueryParams = UriComponentsBuilder.fromUri(source).build().getQueryParams();
        if (currentQueryParams.isEmpty()) {
            return source;
        }
        final var builder = UriComponentsBuilder.fromUri(source);
        boolean mutated = false;

        for (final var entry : queryParamRedactions.entrySet()) {
            if (currentQueryParams.containsKey(entry.getKey())) {
                builder.replaceQueryParam(entry.getKey(), entry.getValue());
                mutated = true;
            }
        }
        return mutated ? builder.build().toUri() : source;
    }
}