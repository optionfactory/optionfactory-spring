package net.optionfactory.spring.upstream.params;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.BooleanExpression;
import net.optionfactory.spring.upstream.expressions.StringExpression;

public class UpstreamAnnotatedCookiesInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, List<AnnotatedCookie>> conf = new ConcurrentHashMap<>();

    private record AnnotatedCookie(BooleanExpression condition, StringExpression value) {

    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Stream.of(endpoint.method().getAnnotationsByType(Upstream.Cookie.class))
                    .map(annotation -> {
                        final var condition = expressions.bool(annotation.condition());
                        final var value = expressions.string(annotation.value(), annotation.valueType());
                        return new AnnotatedCookie(condition, value);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }

    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final var annotatedHeaders = conf.get(invocation.endpoint().method());
        final var ectx = invocation.expressions().context(invocation, request);
        for (final var annotatedHeader : annotatedHeaders) {
            if (!annotatedHeader.condition().evaluate(ectx)) {
                continue;
            }
            request.headers().add("Cookie", annotatedHeader.value().evaluate(ectx));
        }
        return execution.execute(invocation, request);
    }

}
