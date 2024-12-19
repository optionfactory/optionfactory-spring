package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.springframework.http.MediaType;

public class BodyRendering {

    public enum Strategy {
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

    public BodyRendering(Map<String, String> namespaces, Map<String, String> attributes, Map<String, String> tags, Map<JsonPointer, String> jsonPtrs) {
        this.xsltRedactor = XsltRedactor.Factory.INSTANCE.create(namespaces, attributes, tags);
        this.jsonRedactor = new JsonRedactor(new ObjectMapper(), jsonPtrs);
    }

    public String render(RequestContext request, Strategy strategy, String infix, int maxSize) {
        final var bodySource = BodySource.of(request.body());
        final var cl = request.headers().getContentLength();
        final var ct = request.headers().getContentType();
        return render(strategy, cl, ct, bodySource, infix, maxSize);
    }

    public String render(ResponseContext response, Strategy strategy, String infix, int maxSize) {
        final var bodySource = response.body().forInspection(false);
        final var cl = response.headers().getContentLength();
        final var ct = response.headers().getContentType();
        return render(strategy, cl, ct, bodySource, infix, maxSize);
    }

    public String render(Strategy strategy, long contentLength, MediaType type, BodySource source, String infix, int maxSize) {
        return switch (strategy) {
            case SIZE ->
                String.format("size: %s", contentLength == -1 ? "<unavailable>" : String.format("%sB", contentLength));
            case ABBREVIATED ->
                abbreviated(source, infix, maxSize);
            case ABBREVIATED_REDACTED ->
                abbreviated(redact(source, type), infix, maxSize);
            case SKIP ->
                "";
        };
    }

    private String redact(BodySource source, MediaType type) {
        try {
            if (MediaType.parseMediaType("application/*+json").isCompatibleWith(type)) {
                return jsonRedactor.redact(source);
            }
            if (MediaType.parseMediaType("application/*+xml").isCompatibleWith(type) || MediaType.parseMediaType("text/*+xml").isCompatibleWith(type)) {
                return xsltRedactor.redact(source);
            }
        } catch (RuntimeException ex) {
            //fallback to oneline
        }
        return new String(source.bytes(), StandardCharsets.UTF_8).replaceAll("[\r\n]+", "");
    }

    private static String abbreviated(String source, String infix, int maxSize) {
        if (source.length() <= maxSize) {
            return source;
        }
        final int abbreviatedSize = maxSize / 2;

        final var prefix = source.substring(0, abbreviatedSize);
        final var suffix = source.substring(source.length() - abbreviatedSize, source.length());
        return prefix + infix + suffix;
    }

    private static String abbreviated(BodySource source, String infix, int maxSize) {
        final var bytes = source.bytes();
        if (bytes.length <= maxSize) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        final int abbreviatedSize = maxSize / 2;
        final var prefix = new String(bytes, 0, abbreviatedSize, StandardCharsets.UTF_8);
        final var suffix = new String(bytes, bytes.length - abbreviatedSize, abbreviatedSize, StandardCharsets.UTF_8);
        return prefix + infix + suffix;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public static final String DEFAULT_REPLACEMENT = "@redacted@";
        private final Map<String, String> namespaces = new HashMap<>();
        private final Map<String, String> tags = new HashMap<>();
        private final Map<String, String> attributes = new HashMap<>();
        private final Map<JsonPointer, String> jsonPtrs = new HashMap<>();

        public Builder namespace(String prefix, String uri) {
            this.namespaces.put(prefix, uri);
            return this;
        }

        /**
         * Configures a replacement for an XML tag.
         *
         * @param tag the tag to be replaced
         * @param replacement the text to be used as a replacement
         * @return this builder
         */
        public Builder tag(String tag, String replacement) {
            this.tags.put(tag, replacement);
            return this;
        }

        /**
         * Configures a replacement for an XML tag with the default replacement.
         *
         * @param tag the tag to be replaced
         * @return this builder
         */
        public Builder tag(String tag) {
            return tag(tag, DEFAULT_REPLACEMENT);
        }

        /**
         * Configures a replacement for an XML attribute.
         *
         * @param attribute the attribute to be replaced
         * @param replacement the text to be used as a replacement
         * @return this builder
         */
        public Builder attr(String attribute, String replacement) {
            this.attributes.put(attribute, replacement);
            return this;
        }

        /**
         * Configures a replacement for an XML attribute with the default
         * replacement.
         *
         * @param attribute the attribute to be replaced
         * @return this builder
         */
        public Builder attr(String attribute) {
            return attr(attribute, DEFAULT_REPLACEMENT);
        }

        /**
         * Configures a replacement for a JSON component identified by the
         * expression.
         *
         * @param jsonPtrExpression the JsonPointer expression
         * @param replacement the replacement text
         * @return this builder
         */
        public Builder jsonPtr(String jsonPtrExpression, String replacement) {
            this.jsonPtrs.put(JsonPointer.valueOf(jsonPtrExpression), replacement);
            return this;
        }

        /**
         * Configures a replacement for a JSON component identified by the
         * expression with the default replacement.
         *
         * @param jsonPtrExpression the JsonPointer expression
         * @return this builder
         */
        public Builder jsonPtr(String jsonPtrExpression) {
            return jsonPtr(jsonPtrExpression, DEFAULT_REPLACEMENT);
        }

        public BodyRendering build() {
            return new BodyRendering(namespaces, attributes, tags, jsonPtrs);
        }
    }

}
