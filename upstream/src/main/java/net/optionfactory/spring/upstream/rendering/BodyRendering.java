package net.optionfactory.spring.upstream.rendering;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

    public BodyRendering(Map<String, String> namespaces, List<String> attributes, List<String> tags, List<JsonPointer> jsonPtrs) {
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

}
