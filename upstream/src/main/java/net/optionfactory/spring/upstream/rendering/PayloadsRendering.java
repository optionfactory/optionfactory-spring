package net.optionfactory.spring.upstream.rendering;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.rendering.ContentClassDetector.ContentClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.json.JsonMapper;

/// Renders request and response payloads as text for the logs and the alerts, compacting and
/// redacting them.
///
/// Configured with `UpstreamBuilder.redact`, defaults to no redaction:
///
/// ```java
/// UpstreamBuilder.create(Client.class)
///         .redact(r -> r
///                 .header("Authorization")
///                 .param("password")
///                 .jsonPtr("/credentials/secret")
///                 .namespace("s", "urn:example:security")
///                 .tag("//s:password")
///                 .attr("@pin"))
///         ...
/// ```
///
/// Bodies are redacted according to their media type:
///
/// - `json` and `+json` subtypes with the configured json pointers, see [JsonRedactor];
/// - `xml` and `+xml` subtypes with the configured tag and attribute patterns, see [XsltRedactor];
/// - `application/x-www-form-urlencoded`, whatever its parameters (a `charset` included), with
///   the configured params, see [FormUrlencodedRedactor];
/// - anything else is rendered as UTF-8 text, as it is, line breaks removed.
///
/// Redaction fails open by choice: a body that cannot be redacted, typically one that does not
/// match its declared media type, is rendered raw (line breaks removed), since it is more valuable
/// for diagnostics than the redaction risk; the fallback is logged as a warning, to be visible.
public class PayloadsRendering {

    private static final Logger logger = LoggerFactory.getLogger(PayloadsRendering.class);

    /// How a multipart payload is rendered.
    public enum MultipartStrategy {
        /// Renders a summary of each part: its headers, and its body as its size, as `size: 123B`,
        /// whatever the bodies strategy (but [BodiesStrategy#SKIP], which renders nothing).
        RENDER_RECAP,
        /// Renders each part, with its headers and body.
        RENDER_PARTS;
    }

    /// How a body is rendered.
    public enum BodiesStrategy {
        /// Renders nothing: the logging interceptor logs no body line at all.
        SKIP,
        /// Renders the `Content-Length` only, as `size: 123B`, or `size: <unavailable>` without one.
        SIZE,
        /// Renders the body as it is, abbreviated; a binary body is described as
        /// `<binary> size: ...`.
        ABBREVIATED,
        /// Renders the body redacted and compacted, then abbreviated; a binary body is described
        /// as `<binary> size: ...`.
        ABBREVIATED_REDACTED;
    }

    /// Whether headers are rendered.
    public enum HeadersStrategy {
        /// Headers are not logged.
        SKIP,
        /// Headers are logged, redacted.
        CONTENT;
    }

    private final XsltRedactor xsltRedactor;
    private final JsonRedactor jsonRedactor;
    private final FormUrlencodedRedactor formUrlencodedRedactor;
    private final UriRedactor uriRedactor;
    
    private final HttpHeadersRedactor headersRedactor;

    /// Usually created by [#builder()].
    ///
    /// @param xsltRedactor redacts xml bodies
    /// @param jsonRedactor redacts json bodies
    /// @param formUrlencodedRedactor redacts form bodies
    /// @param uriRedactor redacts request uris
    /// @param headersRedactor redacts headers
    public PayloadsRendering(XsltRedactor xsltRedactor, JsonRedactor jsonRedactor, FormUrlencodedRedactor formUrlencodedRedactor, UriRedactor uriRedactor, HttpHeadersRedactor headersRedactor) {
        this.xsltRedactor = xsltRedactor;
        this.jsonRedactor = jsonRedactor;
        this.formUrlencodedRedactor = formUrlencodedRedactor;
        this.uriRedactor = uriRedactor;
        this.headersRedactor = headersRedactor;
    }

    /// A rendered message, or a rendered part of a multipart message.
    ///
    /// @param headers the headers, redacted when the strategy asks for them
    /// @param body the rendered body
    public record RenderedPart(@NonNull HttpHeaders headers, @NonNull String body) {

    }

    /// A rendered request.
    ///
    /// @param uri the request uri, query parameters redacted
    /// @param main the request headers and body; for a multipart request the body is empty, or
    /// `<malformed-multipart>` when no part could be parsed
    /// @param parts the rendered parts of a multipart request, empty otherwise
    public record RenderedRequest(@NonNull URI uri, @NonNull RenderedPart main, @NonNull List<RenderedPart> parts) {

    }

    /// A rendered response.
    ///
    /// @param main the response headers and body; for a multipart response the body is empty, or
    /// `<malformed-multipart>` when no part could be parsed
    /// @param parts the rendered parts of a multipart response, empty otherwise
    public record RenderedResponse(@NonNull RenderedPart main, @NonNull List<RenderedPart> parts) {

    }

    /// Renders a request. Its headers are redacted whatever the headers strategy, which only tells
    /// the caller whether to log them.
    ///
    /// Each part of a multipart request is rendered, and redacted, according to its own
    /// `Content-Type`.
    ///
    /// @param request the request
    /// @param mps the multipart strategy
    /// @param hs the headers strategy, unused
    /// @param bs the bodies strategy
    /// @param infix the abbreviation infix
    /// @param maxSize the abbreviation size
    /// @return the rendered request
    public RenderedRequest render(RequestContext request, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
        final var bodySource = BodySource.of(request.body());
        final var cl = request.headers().getContentLength();
        final var ct = request.headers().getContentType();
        final var redactedUri = uriRedactor.redact(request.uri());
        final var redactedHeaders = headersRedactor.redact(request.headers());
        if (MultipartParser.isMultipart(ct)) {
            final var parts = renderParts(bodySource, ct, mps, bs, infix, maxSize);
            final var main = new RenderedPart(redactedHeaders, parts.isEmpty() ? "<malformed-multipart>" : "");
            return new RenderedRequest(redactedUri, main, parts);
        }
        final var br = renderBody(bs, cl, ct, bodySource, infix, maxSize);

        return new RenderedRequest(redactedUri, new RenderedPart(redactedHeaders, br), List.of());
    }

    /// Renders a response. A body that is not buffered (a streamed one) is rendered as
    /// `<unavailable>` instead of being consumed.
    ///
    /// Each part of a multipart response is rendered, and redacted, according to its own
    /// `Content-Type`.
    ///
    /// @param response the response
    /// @param mps the multipart strategy
    /// @param hs the headers strategy: the headers are redacted unless it is
    /// [HeadersStrategy#SKIP]
    /// @param bs the bodies strategy
    /// @param infix the abbreviation infix
    /// @param maxSize the abbreviation size
    /// @return the rendered response
    public RenderedResponse render(ResponseContext response, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
        final var bodySource = response.body().forInspection(false);
        final var cl = response.headers().getContentLength();
        final var ct = response.headers().getContentType();
        final var redactedHeaders = hs == HeadersStrategy.SKIP ? response.headers() : headersRedactor.redact(response.headers());
        if (MultipartParser.isMultipart(ct)) {
            final var parts = renderParts(bodySource, ct, mps, bs, infix, maxSize);
            final var main = new RenderedPart(redactedHeaders, parts.isEmpty() ? "<malformed-multipart>" : "");
            return new RenderedResponse(main, parts);
        }
        final var br = renderBody(bs, cl, ct, bodySource, infix, maxSize);
        return new RenderedResponse(new RenderedPart(redactedHeaders, br), List.of());

    }

    private List<RenderedPart> renderParts(BodySource bodySource, @Nullable MediaType mediaType, MultipartStrategy mps, BodiesStrategy bs, String infix, int maxSize) {
        final var recap = mps == MultipartStrategy.RENDER_RECAP && bs != BodiesStrategy.SKIP;
        try {
            return MultipartParser.parse(bodySource, mediaType).stream()
                    .map(p -> new RenderedPart(p.headers(), recap
                    ? renderBody(BodiesStrategy.SIZE, p.body().length, p.headers().getContentType(), BodySource.of(p.body()), infix, maxSize)
                    : renderBody(bs, p.headers().getContentLength(), p.headers().getContentType(), BodySource.of(p.body()), infix, maxSize)))
                    .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /// Renders a body according to a strategy.
    ///
    /// `maxSize` counts UTF-8 bytes for [BodiesStrategy#ABBREVIATED], and chars of the redacted
    /// text for [BodiesStrategy#ABBREVIATED_REDACTED]; see [Abbreviations].
    ///
    /// @param strategy the bodies strategy
    /// @param contentLength the declared length, `-1` when unknown, only used to describe sizes
    /// @param type the declared media type, or `null`: selects the redaction and helps telling
    /// binary from text, see [ContentClassDetector]
    /// @param source the body
    /// @param infix the abbreviation infix
    /// @param maxSize the abbreviation size
    /// @return the rendered body, empty for [BodiesStrategy#SKIP]
    public String renderBody(BodiesStrategy strategy, long contentLength, @Nullable MediaType type, BodySource source, String infix, int maxSize) {
        return switch (strategy) {
            case SIZE ->
                String.format("size: %s", contentLength == -1 ? "<unavailable>" : String.format("%sB", contentLength));
            case ABBREVIATED -> {
                if (ContentClass.BINARY == ContentClassDetector.detect(type, source.bytes())) {
                    yield String.format("<binary> size: %s", contentLength == -1 ? "<unavailable>" : String.format("%sB", contentLength));
                }
                yield Abbreviations.abbreviated(source.bytes(), infix, maxSize);
            }
            case ABBREVIATED_REDACTED -> {
                if (ContentClass.BINARY == ContentClassDetector.detect(type, source.bytes())) {
                    yield String.format("<binary> size: %s", contentLength == -1 ? "<unavailable>" : String.format("%sB", contentLength));
                }
                yield Abbreviations.abbreviated(redact(source, type), infix, maxSize);
            }
            case SKIP ->
                "";
        };
    }

    private String redact(BodySource source, MediaType type) {
        try {
            if (type != null) {
                final var subtype = type.getSubtype();
                if ("json".equals(subtype) || subtype.endsWith("+json")) {
                    return jsonRedactor.redact(source);
                }
                if ("xml".equals(subtype) || subtype.endsWith("+xml")) {
                    return xsltRedactor.redact(source);
                }
                if (MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(type)) {
                    return formUrlencodedRedactor.redact(source);
                }
            }
        } catch (RuntimeException ex) {
            logger.warn("cannot redact a {} body: rendering it raw", type, ex);
        }
        return new String(source.bytes(), StandardCharsets.UTF_8).replaceAll("[\r\n]+", "");
    }


    /// @return a builder with no redaction configured
    public static Builder builder() {
        return new Builder();
    }

    /// The redaction rules of a [PayloadsRendering], as exposed by `UpstreamBuilder.redact`.
    ///
    /// Configuring the same tag, attribute, pointer, param or header twice keeps the last
    /// replacement. The single-argument variants use [Builder#DEFAULT_REPLACEMENT].
    public interface Configurer {

        /// Declares a namespace prefix for the tag and attribute patterns. The prefix stands for
        /// the namespace uri, whatever prefix the logged documents use for it.
        ///
        /// @param prefix the prefix used in the patterns
        /// @param uri the namespace uri
        /// @return this configurer
        Configurer namespace(String prefix, String uri);

        /// Replaces the content of the xml elements an XSLT pattern matches, child elements
        /// included; their attributes are kept.
        ///
        /// @param tagExpression the XSLT match pattern, e.g. `//password`
        /// @param replacement the literal text replacing the content
        /// @return this configurer
        Configurer tag(String tagExpression, String replacement);

        /// Replaces the content of the xml elements an XSLT pattern matches with the default
        /// replacement.
        ///
        /// @param tagExpression the XSLT match pattern, e.g. `//password`
        /// @return this configurer
        Configurer tag(String tagExpression);

        /// Replaces the value of the xml attributes an XSLT pattern matches.
        ///
        /// @param attrExpression the XSLT match pattern, e.g. `@password`
        /// @param replacement the literal replacement value
        /// @return this configurer
        Configurer attr(String attrExpression, String replacement);

        /// Replaces the value of the xml attributes an XSLT pattern matches with the default
        /// replacement.
        ///
        /// @param attrExpression the XSLT match pattern, e.g. `@password`
        /// @return this configurer
        Configurer attr(String attrExpression);

        /// Replaces the json value a pointer addresses, whatever its type, with a string.
        ///
        /// @param jsonPtrExpression the json pointer, e.g. `/credentials/0/secret`
        /// @param replacement the replacement string
        /// @return this configurer
        /// @throws IllegalArgumentException when the pointer is malformed
        Configurer jsonPtr(String jsonPtrExpression, String replacement);

        /// Replaces the json value a pointer addresses, whatever its type, with the default
        /// replacement.
        ///
        /// @param jsonPtrExpression the json pointer, e.g. `/credentials/0/secret`
        /// @return this configurer
        /// @throws IllegalArgumentException when the pointer is malformed
        Configurer jsonPtr(String jsonPtrExpression);

        /// Replaces the values of a parameter, both in the request uri query and in form
        /// (`application/x-www-form-urlencoded`) bodies.
        ///
        /// @param qparam the parameter name, matched case-sensitively
        /// @param replacement the replacement value
        /// @return this configurer
        Configurer param(String qparam, String replacement);

        /// Replaces the values of a parameter, both in the request uri query and in form bodies,
        /// with the default replacement.
        ///
        /// @param qparam the parameter name, matched case-sensitively
        /// @return this configurer
        Configurer param(String qparam);

        /// Replaces the values of a header.
        ///
        /// @param header the header name, matched case-insensitively
        /// @param replacement the replacement value
        /// @return this configurer
        Configurer header(String header, String replacement);

        /// Replaces the values of a header with the default replacement.
        ///
        /// @param header the header name, matched case-insensitively
        /// @return this configurer
        Configurer header(String header);

    }

    /// Collects the redaction rules and builds the [PayloadsRendering]; not thread-safe, meant to be
    /// configured then built once.
    public static class Builder implements Configurer {

        /// The replacement of the single-argument rules.
        public static final String DEFAULT_REPLACEMENT = "@redacted@";
        private final Map<String, String> namespaces = new HashMap<>();
        private final Map<String, String> tags = new HashMap<>();
        private final Map<String, String> attributes = new HashMap<>();
        private final Map<JsonPointer, String> jsonPtrs = new HashMap<>();
        private final Map<String, String> headerRedactions = new HashMap<>();
        private final Map<String, String> paramsRedactions = new HashMap<>();

        /// Declares a namespace prefix for the xml patterns, see [Configurer#namespace(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder namespace(String prefix, String uri) {
            this.namespaces.put(prefix, uri);
            return this;
        }

        /// Redacts the content of matching xml elements, see [Configurer#tag(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder tag(String tag, String replacement) {
            this.tags.put(tag, replacement);
            return this;
        }

        /// Redacts the content of matching xml elements with [#DEFAULT_REPLACEMENT].
        ///
        /// @return this builder
        @Override
        public Builder tag(String tag) {
            return tag(tag, DEFAULT_REPLACEMENT);
        }

        /// Redacts the value of matching xml attributes, see [Configurer#attr(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder attr(String attribute, String replacement) {
            this.attributes.put(attribute, replacement);
            return this;
        }

        /// Redacts the value of matching xml attributes with [#DEFAULT_REPLACEMENT].
        ///
        /// @return this builder
        @Override
        public Builder attr(String attribute) {
            return attr(attribute, DEFAULT_REPLACEMENT);
        }

        /// Redacts the json value a pointer addresses, see [Configurer#jsonPtr(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder jsonPtr(String jsonPtrExpression, String replacement) {
            this.jsonPtrs.put(JsonPointer.valueOf(jsonPtrExpression), replacement);
            return this;
        }

        /// Redacts the json value a pointer addresses with [#DEFAULT_REPLACEMENT].
        ///
        /// @return this builder
        @Override
        public Builder jsonPtr(String jsonPtrExpression) {
            return jsonPtr(jsonPtrExpression, DEFAULT_REPLACEMENT);
        }

        /// Redacts a query or form parameter, see [Configurer#param(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder param(String requestParam, String replacement) {
            paramsRedactions.put(requestParam, replacement);
            return this;
        }

        /// Redacts a query or form parameter with [#DEFAULT_REPLACEMENT].
        ///
        /// @return this builder
        @Override
        public Builder param(String requestParam) {
            return param(requestParam, DEFAULT_REPLACEMENT);
        }

        /// Redacts a header, see [Configurer#header(String, String)].
        ///
        /// @return this builder
        @Override
        public Builder header(String header, String replacement) {
            headerRedactions.put(header, replacement);
            return this;
        }

        /// Redacts a header with [#DEFAULT_REPLACEMENT].
        ///
        /// @return this builder
        @Override
        public Builder header(String header) {
            return header(header, DEFAULT_REPLACEMENT);
        }

        /// @return a rendering applying the rules configured so far
        /// @throws IllegalStateException when the xml rules do not compile into a stylesheet
        public PayloadsRendering build() {
            final var xsltRedactor = XsltRedactor.Factory.INSTANCE.create(namespaces, attributes, tags);
            final var jsonRedactor = new JsonRedactor(new JsonMapper(), jsonPtrs);
            final var formUrlEncodedRedactor = new FormUrlencodedRedactor(paramsRedactions);
            final var uriRedactor = new UriRedactor(paramsRedactions);
            final var headersRedactor = new HttpHeadersRedactor(headerRedactions);
            return new PayloadsRendering(xsltRedactor, jsonRedactor, formUrlEncodedRedactor, uriRedactor, headersRedactor);
        }
    }

}
