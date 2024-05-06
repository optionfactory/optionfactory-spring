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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

public class MockResourcesUpstreamHttpResponseFactory implements UpstreamHttpResponseFactory {

    private final TemplateParserContext templateParserContext = new TemplateParserContext();
    private final SpelExpressionParser parser;
    private final Map<Method, List<MockConfiguration>> methodToMockConfigurations = new ConcurrentHashMap<>();

    public MockResourcesUpstreamHttpResponseFactory() {
        this.parser = new SpelExpressionParser();
    }

    private record MockEvaluationContext(String upstream, String endpoint, Map<String, Object> args) {

    }

    private record MockConfiguration(HttpStatus status, Optional<MediaType> defaultMediaType, Expression[] headers, Expression bodyPath) {

    }

    @Override
    public void prepare(Class<?> klass) {
        for (Method m : klass.getMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.isDefault()) {
                continue;
            }
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
                final Expression[] headers = Stream.of(annotation.headers())
                        .map(header -> parser.parseExpression(header, templateParserContext))
                        .toArray(i -> new Expression[i]);
                final Expression bodyPath = parser.parseExpression(annotation.value(), templateParserContext);
                mockConfigurations.add(new MockConfiguration(status, defaultMediaType, headers, bodyPath));
            }
            methodToMockConfigurations.put(m, mockConfigurations);
        }
    }

    @Override
    public ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers) {
        final var mcs = methodToMockConfigurations.get(invocation.method());
        if (mcs == null) {
            throw new RestClientException(String.format("mock resources not configured for %s:%s", invocation.upstream(), invocation.endpoint()));
        }

        final var args = IntStream.range(0, invocation.method().getParameters().length).mapToObj(i -> i).collect(Collectors.toMap(i -> invocation.method().getParameters()[i].getName(), i -> invocation.arguments()[i]));
        final var context = new StandardEvaluationContext(new MockEvaluationContext(invocation.upstream(), invocation.endpoint(), args));
        context.addPropertyAccessor(new MapAccessor());
        for (MockConfiguration mc : mcs) {
            final var path = mc.bodyPath().getValue(context, String.class);
            final var resource = new ClassPathResource(path, invocation.method().getDeclaringClass());
            if (!resource.exists()) {
                continue;
            }
            final var responseHeaders = new HttpHeaders();
            mc.defaultMediaType().ifPresent(responseHeaders::setContentType);
            responseHeaders.addAll(headersFromResource(path, invocation));
            Stream.of(mc.headers())
                    .map(he -> he.getValue(context, String.class))
                    .map(MockResourcesUpstreamHttpResponseFactory::headerFromLine)
                    .map(kv -> new String[]{kv[0].trim(), kv[1].trim()})
                    .forEach(kv -> responseHeaders.add(kv[0], kv[1]));

            return new MockClientHttpResponse(mc.status(), mc.status().getReasonPhrase(), responseHeaders, resource);
        }
        throw new RestClientException(String.format("mock resource not found for %s:%s", invocation.upstream(), invocation.endpoint()));
    }

    private static String[] headerFromLine(String headerLine) {
        final var kv = headerLine.split(":", 2);
        return new String[]{
            kv[0].trim(), kv[1].trim()
        };
    }

    public static HttpHeaders headersFromResource(String path, InvocationContext invocation) {
        final String hp = String.format("%s.headers", path);
        final var resource = new ClassPathResource(hp, invocation.method().getDeclaringClass());
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
            throw new RestClientException(String.format("unreadable mock headers resource %s for %s:%s", hp, invocation.upstream(), invocation.endpoint()));
        }
    }

}
