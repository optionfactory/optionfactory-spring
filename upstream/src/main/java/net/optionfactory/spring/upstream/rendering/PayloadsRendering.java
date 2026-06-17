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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.json.JsonMapper;

public class PayloadsRendering {

    public enum MultipartStrategy {
        RENDER_RECAP,
        RENDER_PARTS;
    }

    public enum BodiesStrategy {
        SKIP,
        SIZE,
        ABBREVIATED,
        ABBREVIATED_REDACTED;
    }

    public enum HeadersStrategy {
        SKIP,
        CONTENT;
    }

    private final XsltRedactor xsltRedactor;
    private final JsonRedactor jsonRedactor;
    private final FormUrlencodedRedactor formUrlencodedRedactor;
    private final UriRedactor uriRedactor;
    
    private final HttpHeadersRedactor headersRedactor;

    public PayloadsRendering(XsltRedactor xsltRedactor, JsonRedactor jsonRedactor, FormUrlencodedRedactor formUrlencodedRedactor, UriRedactor uriRedactor, HttpHeadersRedactor headersRedactor) {
        this.xsltRedactor = xsltRedactor;
        this.jsonRedactor = jsonRedactor;
        this.formUrlencodedRedactor = formUrlencodedRedactor;
        this.uriRedactor = uriRedactor;
        this.headersRedactor = headersRedactor;
    }

    public record RenderedPart(@NonNull HttpHeaders headers, @NonNull String body) {

    }

    public record RenderedRequest(@NonNull URI uri, @NonNull RenderedPart main, @NonNull List<RenderedPart> parts) {

    }

    public record RenderedResponse(@NonNull RenderedPart main, @NonNull List<RenderedPart> parts) {

    }

    public RenderedRequest render(RequestContext request, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
        final var bodySource = BodySource.of(request.body());
        final var cl = request.headers().getContentLength();
        final var ct = request.headers().getContentType();
        final var redactedUri = uriRedactor.redact(request.uri());
        final var redactedHeaders = headersRedactor.redact(request.headers());
        if (MultipartParser.isMultipart(ct)) {
            final var parts = renderParts(bodySource, ct, bs, infix, maxSize);
            final var main = new RenderedPart(redactedHeaders, parts.isEmpty() ? "<malformed-multipart>" : "");
            return new RenderedRequest(redactedUri, main, parts);
        }
        final var br = renderBody(bs, cl, ct, bodySource, infix, maxSize);

        return new RenderedRequest(redactedUri, new RenderedPart(redactedHeaders, br), List.of());
    }

    public RenderedResponse render(ResponseContext response, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
        final var bodySource = response.body().forInspection(false);
        final var cl = response.headers().getContentLength();
        final var ct = response.headers().getContentType();
        final var redactedHeaders = hs == HeadersStrategy.SKIP ? response.headers() : headersRedactor.redact(response.headers());
        if (MultipartParser.isMultipart(ct)) {
            final var parts = renderParts(bodySource, ct, bs, infix, maxSize);
            final var main = new RenderedPart(redactedHeaders, parts.isEmpty() ? "<malformed-multipart>" : "");
            return new RenderedResponse(main, parts);
        }
        final var br = renderBody(bs, cl, ct, bodySource, infix, maxSize);
        return new RenderedResponse(new RenderedPart(redactedHeaders, br), List.of());

    }

    private List<RenderedPart> renderParts(BodySource bodySource, @Nullable MediaType mediaType, BodiesStrategy bs, String infix, int maxSize) {
        try {
            return MultipartParser.parse(bodySource, mediaType).stream()
                    .map(p -> new RenderedPart(p.headers(), renderBody(bs, p.headers().getContentLength(), mediaType, BodySource.of(p.body()), infix, maxSize)))
                    .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

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
                if(type.equals(MediaType.APPLICATION_FORM_URLENCODED)){
                    return formUrlencodedRedactor.redact(source);
                }
            }
        } catch (RuntimeException ex) {
            //fallback to unredacted oneline
        }
        return new String(source.bytes(), StandardCharsets.UTF_8).replaceAll("[\r\n]+", "");
    }


    public static Builder builder() {
        return new Builder();
    }

    public interface Configurer {

        /**
         * Namespaces that will be imported by the used xsl:stylesheet
         *
         * @param prefix the prefix
         * @param uri the uri
         * @return this configurer
         */
        Configurer namespace(String prefix, String uri);

        /**
         * Configures a replacement for an XML tag.
         *
         * @param tagExpression the XSLT tag expression matching tags to be
         * replaced
         * @param replacement the text to be used as a replacement
         * @return this configurer
         */
        Configurer tag(String tagExpression, String replacement);

        /**
         * Configures a replacement for an XML tag with the default replacement.
         *
         * @param tagExpression the XSLT tag expression matching tags to be
         * replaced
         * @return this configurer
         */
        Configurer tag(String tagExpression);

        /**
         * Configures a replacement for an XML attribute.
         *
         * @param attrExpression the XSLT attribute expression matching
         * attributes to be replaced
         * @param replacement the text to be used as a replacement
         * @return this configurer
         */
        Configurer attr(String attrExpression, String replacement);

        /**
         * Configures a replacement for an XML attribute with the default
         * replacement.
         *
         * @param attrExpression the XSLT attribute expression matching
         * attributes to be replaced
         * @return this configurer
         */
        Configurer attr(String attrExpression);

        /**
         * Configures a replacement for a JSON component identified by the
         * expression.
         *
         * @param jsonPtrExpression the JsonPointer expression
         * @param replacement the replacement text
         * @return this configurer
         */
        Configurer jsonPtr(String jsonPtrExpression, String replacement);

        /**
         * Configures a replacement for a JSON component identified by the
         * expression with the default replacement.
         *
         * @param jsonPtrExpression the JsonPointer expression
         * @return this configurer
         */
        Configurer jsonPtr(String jsonPtrExpression);

        /**
         * Configures a replacement for a request parameter.
         *
         * @param qparam the query parameter
         * @param replacement the replacement
         * @return this configurer
         */
        Configurer param(String qparam, String replacement);

        /**
         * Configures a replacement for a request parameter.
         *
         * @param qparam the query parameter
         * @return this configurer
         */
        Configurer param(String qparam);

        /**
         * Configures a replacement for an HTTP header.
         *
         * @param header the header
         * @param replacement the replacement
         * @return this configurer
         */
        Configurer header(String header, String replacement);

        /**
         * Configures a replacement for an HTTP header.
         *
         * @param header the header
         * @return this configurer
         */
        Configurer header(String header);

    }

    public static class Builder implements Configurer {

        public static final String DEFAULT_REPLACEMENT = "@redacted@";
        private final Map<String, String> namespaces = new HashMap<>();
        private final Map<String, String> tags = new HashMap<>();
        private final Map<String, String> attributes = new HashMap<>();
        private final Map<JsonPointer, String> jsonPtrs = new HashMap<>();
        private final Map<String, String> headerRedactions = new HashMap<>();
        private final Map<String, String> paramsRedactions = new HashMap<>();

        @Override
        public Builder namespace(String prefix, String uri) {
            this.namespaces.put(prefix, uri);
            return this;
        }

        @Override
        public Builder tag(String tag, String replacement) {
            this.tags.put(tag, replacement);
            return this;
        }

        @Override
        public Builder tag(String tag) {
            return tag(tag, DEFAULT_REPLACEMENT);
        }

        @Override
        public Builder attr(String attribute, String replacement) {
            this.attributes.put(attribute, replacement);
            return this;
        }

        @Override
        public Builder attr(String attribute) {
            return attr(attribute, DEFAULT_REPLACEMENT);
        }

        @Override
        public Builder jsonPtr(String jsonPtrExpression, String replacement) {
            this.jsonPtrs.put(JsonPointer.valueOf(jsonPtrExpression), replacement);
            return this;
        }

        @Override
        public Builder jsonPtr(String jsonPtrExpression) {
            return jsonPtr(jsonPtrExpression, DEFAULT_REPLACEMENT);
        }

        @Override
        public Builder param(String qparam, String replacement) {
            paramsRedactions.put(qparam, replacement);
            return this;
        }

        @Override
        public Builder param(String qparam) {
            return param(qparam, DEFAULT_REPLACEMENT);
        }

        @Override
        public Builder header(String header, String replacement) {
            headerRedactions.put(header, replacement);
            return this;
        }

        @Override
        public Builder header(String header) {
            return header(header, DEFAULT_REPLACEMENT);
        }

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
