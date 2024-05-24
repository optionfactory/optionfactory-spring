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
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

public class MockResourcesUpstreamHttpResponseFactory implements UpstreamHttpResponseFactory {

    private final Map<Method, List<MockConfiguration>> methodToMockConfigurations = new ConcurrentHashMap<>();

    private record MockConfiguration(HttpStatus status, Optional<MediaType> defaultMediaType, StringExpression[] headers, StringExpression bodyPath) {

    }

    private final Logger logger = LoggerFactory.getLogger(MockResourcesUpstreamHttpResponseFactory.class);

    @Override
    public void preprocess(Class<?> klass, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var m = endpoint.method();
            final var conf = m.getAnnotationsByType(Upstream.Mock.class);
            if (conf.length == 0) {
                continue;
            }

            final var defaultMediaType = Optional
                    .ofNullable(m.getDeclaringClass().getAnnotation(Upstream.Mock.DefaultContentType.class))
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
                mockConfigurations.add(new MockConfiguration(status, defaultMediaType, headers, bodyPath));
            }
            if (mockConfigurations.isEmpty()) {
                logger.warn("missing mock configuration in {}:{}", klass, m);
            }
            methodToMockConfigurations.put(m, mockConfigurations);
        }

    }

    @Override
    public ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers) {
        final var mcs = methodToMockConfigurations.get(invocation.endpoint().method());
        final var context = invocation.expressions().context(invocation);
        for (MockConfiguration mc : mcs) {
            final var path = mc.bodyPath().evaluate(context);
            final var resource = new ClassPathResource(path, invocation.endpoint().method().getDeclaringClass());
            if (!resource.exists()) {
                continue;
            }
            final var responseHeaders = new HttpHeaders();
            mc.defaultMediaType().ifPresent(responseHeaders::setContentType);
            responseHeaders.addAll(headersFromResource(path, invocation));
            Stream.of(mc.headers())
                    .map(he -> he.evaluate(context))
                    .map(MockResourcesUpstreamHttpResponseFactory::headerFromLine)
                    .map(kv -> new String[]{kv[0].trim(), kv[1].trim()})
                    .forEach(kv -> responseHeaders.add(kv[0], kv[1]));

            return new MockClientHttpResponse(mc.status(), mc.status().getReasonPhrase(), responseHeaders, resource);
        }
        throw new RestClientException(String.format("mock resource not found for %s:%s", invocation.endpoint().upstream(), invocation.endpoint().name()));
    }

    private static String[] headerFromLine(String headerLine) {
        final var kv = headerLine.split(":", 2);
        return new String[]{
            kv[0].trim(), kv[1].trim()
        };
    }

    public static HttpHeaders headersFromResource(String path, InvocationContext invocation) {
        final String hp = String.format("%s.headers", path);
        final var resource = new ClassPathResource(hp, invocation.endpoint().method().getDeclaringClass());
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
            throw new RestClientException(String.format("unreadable mock headers resource %s for %s:%s", hp, invocation.endpoint().upstream(), invocation.endpoint().name()));
        }
    }

}
