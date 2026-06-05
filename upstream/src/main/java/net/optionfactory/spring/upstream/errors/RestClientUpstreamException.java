package net.optionfactory.spring.upstream.errors;

import java.util.Optional;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

public class RestClientUpstreamException extends RestClientResponseException {

    public final String upstream;
    public final String endpoint;
    public final String reason;

    public RestClientUpstreamException(
            MessageConverters converters,
            String upstream,
            String endpoint,
            String reason,
            HttpStatusCode statusCode,
            String statusText,
            @Nullable HttpHeaders headers,
            @Nullable byte[] responseBody
    ) {

        super(String.format("Upstream error for %s:%s: %s", upstream, endpoint, reason), statusCode, statusText, headers, responseBody, Optional.ofNullable(headers)
                .map(h -> h.getContentType())
                .map(ct -> ct.getCharset())
                .orElse(null));
        this.upstream = upstream;
        this.endpoint = endpoint;
        this.reason = reason;
        this.setBodyConvertFunction(resolvableType -> converters.convert(responseBody, resolvableType, headers));
    }

}
