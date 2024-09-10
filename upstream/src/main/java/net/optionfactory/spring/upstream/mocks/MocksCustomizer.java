package net.optionfactory.spring.upstream.mocks;

import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamBuilder.RequestFactoryConfigurer;
import net.optionfactory.spring.upstream.mocks.rendering.StaticRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.ThymeleafRenderer;
import org.springframework.http.client.ClientHttpResponse;
import org.thymeleaf.dialect.IDialect;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

public class MocksCustomizer {

    private UpstreamHttpResponseFactory responseFactory;
    private MocksRenderer renderer;

    public MocksCustomizer responseFactory(UpstreamHttpResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
        return this;
    }

    public MocksCustomizer renderer(MocksRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    public MocksCustomizer thymeleaf(@Nullable MessageSource ms, IDialect... dialects) {
        this.renderer = new ThymeleafRenderer(ms, dialects);
        return this;
    }

    public MocksCustomizer response(ClientHttpResponse o) {
        this.responseFactory = (invocation, uri, method, headers) -> o;
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
        this.responseFactory = (invocation, uri, method, rhs) -> new MockClientHttpResponse(status, status.getReasonPhrase(), headers, new ByteArrayResource(body));
        return this;
    }

    public RequestFactoryConfigurer toRequestFactoryConfigurer() {
        return (scopeHandler, klass, expressions, endpoints) -> {
            final var rf = responseFactory != null
                    ? responseFactory
                    : new MockResourcesUpstreamHttpResponseFactory(renderer != null ? renderer : new StaticRenderer());

            final var hrf = new MockUpstreamRequestFactory(rf);
            hrf.preprocess(klass, expressions, endpoints);
            return scopeHandler.adapt(hrf);
        };
    }
}
