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

/// Configures the mocked request factory of `UpstreamBuilder.requestFactoryMock`.
///
/// A mocked client answers in one of two ways:
///
/// - with a response factory, set by [#responseFactory(UpstreamHttpResponseFactory)] or by one of
///   the `response` shortcuts, which answers every exchange;
/// - otherwise with the classpath resources named by the endpoints' `@Upstream.Mock` annotations
///   (see [MockResourcesUpstreamHttpResponseFactory]), rendered by the renderers registered here.
///
/// Setting a response factory replaces any previous one, the last call wins, and makes the
/// registered renderers irrelevant. Renderers are tried in registration order, the first accepting
/// a resource renders it, and a resource no renderer accepts is served as it is.
///
/// ```java
/// UpstreamBuilder.create(Client.class)
///         .requestFactoryMockIf(mocked, c -> c.defaults(applicationContext))
///         .json(mapper)
///         .applicationContext(applicationContext)
///         .baseUri(baseUri)
///         .build();
/// ```
public class MocksCustomizer {

    private final AtomicReference<UpstreamHttpResponseFactory> responseFactory;
    private final List<MocksRenderer> renderers;

    /// Created by `UpstreamBuilder.requestFactoryMock`, which reads what is configured once the
    /// customizer returns.
    ///
    /// @param responseFactory receives the configured response factory, if any
    /// @param renderers receives the registered renderers, in order
    public MocksCustomizer(AtomicReference<UpstreamHttpResponseFactory> responseFactory, List<MocksRenderer> renderers) {
        this.responseFactory = responseFactory;
        this.renderers = renderers;
    }

    /// Answers every exchange with the given factory, instead of the `@Upstream.Mock` resources.
    ///
    /// @param responseFactory the factory creating the response of each exchange
    /// @return this customizer
    public MocksCustomizer responseFactory(UpstreamHttpResponseFactory responseFactory) {
        this.responseFactory.set(responseFactory);
        return this;
    }

    /// Registers a renderer for the `@Upstream.Mock` resources, after the ones already registered.
    ///
    /// @param renderer the renderer
    /// @return this customizer
    public MocksCustomizer renderer(MocksRenderer renderer) {
        this.renderers.add(renderer);
        return this;
    }

    /// Registers the json template renderer ([#jsont()]) followed by the thymeleaf one
    /// ([#thymeleaf(MessageSource, IDialect...)]).
    ///
    /// @param messageSource resolves the `#{...}` messages of thymeleaf templates, or `null`
    /// @param dialects additional thymeleaf dialects
    /// @return this customizer
    public MocksCustomizer defaults(@Nullable MessageSource messageSource, IDialect... dialects) {
        return jsont().thymeleaf(messageSource, dialects);
    }

    /// Registers the json template renderer ([#jsont()]) followed by the thymeleaf one, without a
    /// message source.
    ///
    /// @param dialects additional thymeleaf dialects
    /// @return this customizer
    public MocksCustomizer defaults(IDialect... dialects) {
        return jsont().thymeleaf(dialects);
    }

    /// Registers a [JsonTemplateRenderer].
    ///
    /// @param templateSuffix the file name suffix of the resources to render
    /// @param mapper the mapper parsing the templates and writing the result
    /// @return this customizer
    public MocksCustomizer jsont(String templateSuffix, JsonMapper mapper) {
        this.renderers.add(new JsonTemplateRenderer(templateSuffix, mapper));
        return this;
    }

    /// Registers a [JsonTemplateRenderer] for the resources ending in `.tpl.json`.
    ///
    /// @param mapper the mapper parsing the templates and writing the result
    /// @return this customizer
    public MocksCustomizer jsont(JsonMapper mapper) {
        return jsont(".tpl.json", mapper);
    }

    /// Registers a [JsonTemplateRenderer] for the resources ending in `.tpl.json`, with a default
    /// `JsonMapper`.
    ///
    /// @return this customizer
    public MocksCustomizer jsont() {
        return jsont(".tpl.json", new JsonMapper());
    }

    /// Registers a [ThymeleafRenderer].
    ///
    /// @param messageSource resolves the `#{...}` messages of the templates, or `null`
    /// @param dialects additional thymeleaf dialects
    /// @param templateSuffixes the file name suffixes of the resources to render
    /// @return this customizer
    public MocksCustomizer thymeleaf(@Nullable MessageSource messageSource, IDialect[] dialects, String[] templateSuffixes) {
        this.renderers.add(new ThymeleafRenderer(messageSource, templateSuffixes, dialects));
        return this;
    }

    /// The suffixes rendered by thymeleaf unless configured otherwise. The template mode follows the
    /// last extension: `.th.json` templates are rendered in javascript mode, where `[[${...}]]`
    /// writes a json literal (a string comes out quoted).
    public static final String[] DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES = new String[]{
        ".th.json",
        ".th.xml",
        ".th.css",
        ".th.html",
        ".th.txt"
    };

    /// Registers a [ThymeleafRenderer] for the [#DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES].
    ///
    /// @param messageSource resolves the `#{...}` messages of the templates, or `null`
    /// @param dialects additional thymeleaf dialects
    /// @return this customizer
    public MocksCustomizer thymeleaf(@Nullable MessageSource messageSource, IDialect... dialects) {
        return thymeleaf(messageSource, dialects, DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES);
    }

    /// Registers a [ThymeleafRenderer] for the [#DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES], without a
    /// message source.
    ///
    /// @param dialects additional thymeleaf dialects
    /// @return this customizer
    public MocksCustomizer thymeleaf(IDialect... dialects) {
        return thymeleaf(null, dialects, DEFAULT_THYMELEAF_TEMPLATE_SUFFIXES);
    }

    /// Answers every exchange with the given response instance. Its body must be re-readable for
    /// the client to be invoked more than once, as the one of a [MockClientHttpResponse] over a
    /// resource is.
    ///
    /// @param o the response
    /// @return this customizer
    public MocksCustomizer response(ClientHttpResponse o) {
        this.responseFactory.set((invocation, uri, method, headers) -> o);
        return this;
    }

    /// Answers every exchange with `200 OK`.
    ///
    /// @param headers the response headers
    /// @param body the response body, encoded as UTF-8
    /// @return this customizer
    public MocksCustomizer response(HttpHeaders headers, String body) {
        return response(HttpStatus.OK, headers, body);
    }

    /// Answers every exchange with `200 OK`.
    ///
    /// @param headers the response headers
    /// @param body the response body
    /// @return this customizer
    public MocksCustomizer response(HttpHeaders headers, byte[] body) {
        return response(HttpStatus.OK, headers, body);
    }

    /// Answers every exchange with `200 OK`.
    ///
    /// @param mediaType the response `Content-Type`
    /// @param body the response body, encoded as UTF-8 whatever the media type charset
    /// @return this customizer
    public MocksCustomizer response(MediaType mediaType, String body) {
        final var headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(HttpStatus.OK, headers, body);
    }

    /// Answers every exchange with `200 OK`.
    ///
    /// @param mediaType the response `Content-Type`
    /// @param body the response body
    /// @return this customizer
    public MocksCustomizer response(MediaType mediaType, byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(HttpStatus.OK, headers, body);
    }

    /// Answers every exchange with the given status.
    ///
    /// @param status the response status, with its standard reason phrase
    /// @param mediaType the response `Content-Type`
    /// @param body the response body, encoded as UTF-8 whatever the media type charset
    /// @return this customizer
    public MocksCustomizer response(HttpStatus status, MediaType mediaType, String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, headers, bytes);
    }

    /// Answers every exchange with the given status.
    ///
    /// @param status the response status, with its standard reason phrase
    /// @param mediaType the response `Content-Type`
    /// @param body the response body
    /// @return this customizer
    public MocksCustomizer response(HttpStatus status, MediaType mediaType, byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return response(status, headers, body);
    }

    /// Answers every exchange with the given status.
    ///
    /// @param status the response status, with its standard reason phrase
    /// @param headers the response headers
    /// @param body the response body, encoded as UTF-8
    /// @return this customizer
    public MocksCustomizer response(HttpStatus status, HttpHeaders headers, String body) {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        return response(status, headers, bytes);
    }

    /// Answers every exchange with the given status; each exchange gets a new response over the
    /// same headers instance and body bytes.
    ///
    /// @param status the response status, with its standard reason phrase
    /// @param headers the response headers
    /// @param body the response body
    /// @return this customizer
    public MocksCustomizer response(HttpStatus status, HttpHeaders headers, byte[] body) {
        this.responseFactory.set((invocation, uri, method, rhs) -> new MockClientHttpResponse(status, status.getReasonPhrase(), headers, new ByteArrayResource(body)));
        return this;
    }

}
