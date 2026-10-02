package net.optionfactory.spring.upstream.errors;

import java.util.Optional;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/// The exception thrown by an upstream client when a response is an error: an error status, or a
/// response matching an `@Upstream.ErrorOnResponse` condition.
///
/// Being a `RestClientResponseException`, it carries the status, the headers and the body of the
/// response; [#getResponseBodyAs(Class)] converts the body with the message converters of the
/// client, and its charset comes from the response `Content-Type`.
///
/// ```java
/// try {
///     client.pay(order);
/// } catch (RestClientUpstreamException ex) {
///     final var failures = ex.getResponseBodyAs(Failures.class);
///     ...
/// }
/// ```
public class RestClientUpstreamException extends RestClientResponseException {

    /// The name of the upstream.
    public final String upstream;
    /// The name of the endpoint.
    public final String endpoint;
    /// Why the response is an error: the status code and text for an error status, the reason of the
    /// matching annotation otherwise.
    public final String reason;

    /// @param converters converts the body for [#getResponseBodyAs(Class)]
    /// @param upstream the name of the upstream
    /// @param endpoint the name of the endpoint
    /// @param reason why the response is an error
    /// @param statusCode the response status
    /// @param statusText the response status text
    /// @param headers the response headers, possibly `null`
    /// @param responseBody the response body, possibly `null`
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
