package net.optionfactory.spring.upstream.mocks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.mocks.rendering.JsonTemplateRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.ThymeleafRenderer;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.thymeleaf.dialect.IDialect;
import tools.jackson.databind.json.JsonMapper;

public class MocksCustomizer {

    private final AtomicReference<UpstreamHttpResponseFactory> responseFactory;
    private final List<MocksRenderer> renderers;

    public MocksCustomizer(AtomicReference<UpstreamHttpResponseFactory> responseFactory, List<MocksRenderer> renderers) {
        this.responseFactory = responseFactory;
        this.renderers = renderers;
    }

    public MocksCustomizer responseFactory(UpstreamHttpResponseFactory responseFactory) {
        this.responseFactory.set(responseFactory);
        return this;
    }

    public MocksCustomizer renderer(MocksRenderer renderer) {
        this.renderers.add(renderer);
        return this;
    }

    public MocksCustomizer defaults(@Nullable MessageSource messageSource, IDialect... dialects) {
        return jsont().thymeleaf(messageSource, dialects);
    }

    public MocksCustomizer defaults(IDialect... dialects) {
        return jsont().thymeleaf(dialects);
    }

    public MocksCustomizer jsont(String templateSuffix, JsonMapper mapper) {
        this.renderers.add(new JsonTemplateRenderer(templateSuffix, mapper));
        return this;
    }

    public MocksCustomizer jsont(JsonMapper mapper) {
        return jsont(".tpl.json", mapper);
    }

    public MocksCustomizer jsont() {
        return jsont(".tpl.json", new JsonMapper());
    }

    public MocksCustomizer thymeleaf(@Nullable MessageSource messageSource, IDialect[] dialects, String[] templateSuffixes) {
        this.renderers.add(new ThymeleafRenderer(messageSource, templateSuffixes, dialects));
        return this;
    }

    public static final String[] DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES = new String[]{
        ".th.json",
        ".th.xml",
        ".th.css",
        ".th.html",
        ".th.txt"
    };

    public MocksCustomizer thymeleaf(@Nullable MessageSource messageSource, IDialect... dialects) {
        return thymeleaf(messageSource, dialects, DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES);
    }

    public MocksCustomizer thymeleaf(IDialect... dialects) {
        return thymeleaf(null, dialects, DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES);
    }

    public MocksCustomizer response(ClientHttpResponse o) {
        this.responseFactory.set((invocation, uri, method, headers) -> o);
        return this;
    }

    public MocksCustomizer response(HttpHeaders headers, String body) {
        return response(HttpStatus.OK, headers, body);
    }

    public MocksCustomizer response(HttpHeaders headers, byte[] body) {
        return response(HttpStatus.OK, headers, body);
    }

    public MocksCustomizer response(MediaType mediaType, String body) {
        final var headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(HttpStatus.OK, headers, body);
    }

    public MocksCustomizer response(MediaType mediaType, byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(HttpStatus.OK, headers, body);
    }

    public MocksCustomizer response(HttpStatus status, MediaType mediaType, String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, headers, bytes);
    }

    public MocksCustomizer response(HttpStatus status, MediaType mediaType, byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(status, headers, body);
    }

    public MocksCustomizer response(HttpStatus status, HttpHeaders headers, String body) {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, headers, bytes);
    }

    public MocksCustomizer response(HttpStatus status, HttpHeaders headers, byte[] body) {
        this.responseFactory.set((invocation, uri, method, rhs) -> new MockClientHttpResponse(status, status.getReasonPhrase(), headers, new ByteArrayResource(body)));
        return this;
    }

}
