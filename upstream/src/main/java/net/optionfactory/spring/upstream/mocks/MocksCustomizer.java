package net.optionfactory.spring.upstream.mocks;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.ThymeleafRenderer;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.thymeleaf.dialect.IDialect;

public class MocksCustomizer {

    private AtomicReference<UpstreamHttpResponseFactory> responseFactory;
    private AtomicReference<MocksRenderer> renderer;

    public MocksCustomizer(AtomicReference<UpstreamHttpResponseFactory> responseFactory, AtomicReference<MocksRenderer> renderer) {
        this.responseFactory = responseFactory;
        this.renderer = renderer;
    }

    public MocksCustomizer responseFactory(UpstreamHttpResponseFactory responseFactory) {
        this.responseFactory.set(responseFactory);
        return this;
    }

    public MocksCustomizer renderer(MocksRenderer renderer) {
        this.renderer.set(renderer);
        return this;
    }

    public MocksCustomizer thymeleaf(@Nullable MessageSource ms, IDialect... dialects) {
        this.renderer.set(new ThymeleafRenderer(".template", ms, dialects));
        return this;
    }

    public MocksCustomizer thymeleaf(String templateSuffix, @Nullable MessageSource ms, IDialect... dialects) {
        this.renderer.set(new ThymeleafRenderer(templateSuffix, ms, dialects));
        return this;
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
