package net.optionfactory.spring.upstream.errors;

import java.nio.charset.Charset;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClientResponseException;

public class RestClientUpstreamException extends RestClientResponseException {

    public RestClientUpstreamException(
            String upstream,
            String endpoint,
            String reason,
            HttpStatusCode statusCode,
            String statusText,
            @Nullable HttpHeaders headers,
            @Nullable byte[] responseBody) {

        super(String.format("Upstream error for %s:%s: %s", upstream, endpoint, reason), statusCode, statusText, headers, responseBody, Optional.ofNullable(headers)
                .map(h -> h.getContentType())
                .map(ct -> ct.getCharset())
                .orElse(null));
    }

}
