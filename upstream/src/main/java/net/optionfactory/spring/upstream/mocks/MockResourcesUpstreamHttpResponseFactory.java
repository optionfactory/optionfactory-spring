package net.optionfactory.spring.upstream.mocks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.mocks.rendering.StaticRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

/// Answers mocked exchanges with classpath resources named by the endpoints' [Upstream.Mock]
/// annotations.
///
/// It is the response factory `UpstreamBuilder.requestFactoryMock` uses when the [MocksCustomizer]
/// configures neither a fixed response nor a response factory of its own.
///
/// For each invocation the `@Upstream.Mock` annotations of the endpoint are tried in declaration
/// order, and the first one whose resource exists answers:
///
/// - the annotation `value` is evaluated (a template by default) with `#upstream`, `#endpoint`,
///   `#invocation`, `#args` and the method parameters by name, and resolved as a classpath resource
///   relative to the interface whose declaration of the method carries the annotations, which is
///   the overridden one for a method redeclared without them (a leading `/` makes it absolute);
/// - the response status is the annotation `status`, with its standard reason phrase;
/// - the response headers are the lines of the `<resource>.headers` resource when present (see
///   [#headersFromResource(String, InvocationContext)]) followed by the annotation `headers`, values
///   added, never replaced; when neither gives a `Content-Type`, the [Upstream.Mock.DefaultContentType]
///   of the class declaring the method (or of its super interfaces) is used;
/// - the body is the resource rendered by the first [MocksRenderer] accepting it, or the resource
///   as it is when none does.
///
/// ```java
/// @Upstream.Mock.DefaultContentType("application/json")
/// public interface Client {
///     @GetExchange("/users/{id}")
///     @Upstream.Mock("users-#{#id}.json")
///     @Upstream.Mock("users-default.json")
///     User user(@PathVariable String id);
/// }
/// ```
///
/// The annotations are read once, by [#preprocess(Class, Expressions, Map)]; afterwards the
/// factory can serve concurrent invocations.
public class MockResourcesUpstreamHttpResponseFactory implements UpstreamHttpResponseFactory {

    private final Map<Method, List<MockConfiguration>> methodToMockConfigurations = new ConcurrentHashMap<>();
    private final List<MocksRenderer> renderers;
    private final StaticRenderer fallbackRenderer;
    
    private record MockConfiguration(Class<?> anchor, HttpStatus status, Optional<MediaType> defaultMediaType, StringExpression[] headers, StringExpression bodyPath) {

    }

    /// @param renderers the renderers to try, in order, on each resolved resource
    public MockResourcesUpstreamHttpResponseFactory(List<MocksRenderer> renderers) {
        this.renderers = renderers;
        this.fallbackRenderer = new StaticRenderer();
    }

    private final Logger logger = LoggerFactory.getLogger(MockResourcesUpstreamHttpResponseFactory.class);

    /// Compiles the [Upstream.Mock] annotations of every endpoint. An endpoint without one is
    /// reported with a warning, and invoking it fails with a `RestClientException`.
    ///
    /// @param klass the client interface
    /// @param expressions the parser of the annotations' expressions
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> klass, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var m = endpoint.method();
            final Class<?> anchor = Annotations.declaration(m, Upstream.Mock.class).map(Method::getDeclaringClass).orElse(m.getDeclaringClass());
            final var conf = Annotations.onMethodRepeatable(m, Upstream.Mock.class);
            final var defaultMediaType = Optional
                    .ofNullable(AnnotationUtils.findAnnotation(m.getDeclaringClass(), Upstream.Mock.DefaultContentType.class))
                    .map(ann -> ann.value())
                    .filter(v -> !v.isBlank())
                    .map(MediaType::parseMediaType);

            final var mockConfigurations = new ArrayList<MockConfiguration>();
            for (Upstream.Mock annotation : conf) {
                final HttpStatus status = annotation.status();
                final StringExpression[] headers = Stream.of(annotation.headers())
                        .map(header -> expressions.string(header, annotation.headersType()))
                        .toArray(i -> new StringExpression[i]);
                final var bodyPath = expressions.string(annotation.value(), annotation.valueType());
                mockConfigurations.add(new MockConfiguration(anchor, status, defaultMediaType, headers, bodyPath));
            }
            if (mockConfigurations.isEmpty()) {
                logger.warn("missing mock configuration in {}:{}", klass, m);
            }
            methodToMockConfigurations.put(m, mockConfigurations);
        }

    }

    /// @param invocation the invocation being mocked
    /// @param uri the request uri, unused
    /// @param method the request method, unused
    /// @param headers the request headers, unused
    /// @return the response built from the first existing mock resource
    /// @throws RestClientException when the endpoint has no annotation, when no annotation names an
    /// existing resource, or when a header line is missing its `:`
    @Override
    public ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers) {
        final var mcs = methodToMockConfigurations.getOrDefault(invocation.endpoint().method(), List.of());
        if (mcs.isEmpty()) {
            throw new RestClientException(String.format("missing mock configuration for %s:%s", invocation.endpoint().upstream(), invocation.endpoint().name()));
        }
        final var context = invocation.expressions().context(invocation);
        for (MockConfiguration mc : mcs) {
            final var path = mc.bodyPath().evaluate(context);
            final var resource = new ClassPathResource(path, mc.anchor());
            if (!resource.exists()) {
                continue;
            }
            final var responseHeaders = headersFromResource(path, mc.anchor(), invocation);
            Stream.of(mc.headers())
                    .map(he -> he.evaluate(context))
                    .map(MockResourcesUpstreamHttpResponseFactory::headerFromLine)
                    .map(kv -> new String[]{kv[0].trim(), kv[1].trim()})
                    .forEach(kv -> responseHeaders.add(kv[0], kv[1]));
            if (!responseHeaders.containsHeader(HttpHeaders.CONTENT_TYPE)) {
                mc.defaultMediaType().ifPresent(responseHeaders::setContentType);
            }

            final var renderer = renderers.stream().
                    filter(mr -> mr.canRender(resource))
                    .findFirst()
                    .orElse(fallbackRenderer);

            return new MockClientHttpResponse(mc.status(), mc.status().getReasonPhrase(), responseHeaders, renderer.render(resource, invocation));
        }
        throw new RestClientException(String.format("mock resource not found for %s:%s", invocation.endpoint().upstream(), invocation.endpoint().name()));
    }

    private static String[] headerFromLine(String headerLine) {
        final var kv = headerLine.split(":", 2);
        if (kv.length < 2) {
            throw new RestClientException(String.format("malformed mock header line (missing ':'): %s", headerLine));
        }
        return new String[]{
            kv[0].trim(), kv[1].trim()
        };
    }

    /// Reads the headers of a mock from the `<path>.headers` resource next to it.
    ///
    /// The resource is read as UTF-8, one `Name: value` header per line; name and value are trimmed,
    /// and a name repeated on more lines gets all of its values. Every line, blank ones included,
    /// must contain a `:`.
    ///
    /// @param path the mock resource path, relative to the class declaring the invoked method
    /// @param invocation the invocation, giving the class the path is relative to
    /// @return the headers, empty when the resource does not exist
    /// @throws RestClientException when a line is missing its `:` or the resource cannot be read
    public static HttpHeaders headersFromResource(String path, InvocationContext invocation) {
        return headersFromResource(path, invocation.endpoint().method().getDeclaringClass(), invocation);
    }

    private static HttpHeaders headersFromResource(String path, Class<?> anchor, InvocationContext invocation) {
        final String hp = String.format("%s.headers", path);
        final var resource = new ClassPathResource(hp, anchor);
        final var headers = new HttpHeaders();
        if (!resource.exists()) {
            return headers;
        }
        try (var r = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            r.lines()
                    .map(MockResourcesUpstreamHttpResponseFactory::headerFromLine)
                    .forEach(kv -> headers.add(kv[0], kv[1]));

            return headers;
        } catch (IOException ex) {
            throw new RestClientException(String.format("unreadable mock headers resource %s for %s:%s", hp, invocation.endpoint().upstream(), invocation.endpoint().name()), ex);
        }
    }

}
