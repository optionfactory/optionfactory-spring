package net.optionfactory.spring.upstream.values;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter.HttpRequestValuesTransformer;
import org.springframework.web.service.invoker.HttpRequestValues;

public class UpstreamAnnotatedPathVariableTransformer implements HttpRequestValuesTransformer {

    private final Map<Method, List<AnnotatedPathVariable>> conf = new ConcurrentHashMap<>();

    private record AnnotatedPathVariable(StringExpression key, StringExpression value) {

    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Stream.of(endpoint.method().getAnnotationsByType(Upstream.PathVariable.class))
                    .map(annotation -> {
                        final var key = expressions.string(annotation.key(), annotation.keyType());
                        final var value = expressions.string(annotation.value(), annotation.valueType());
                        return new AnnotatedPathVariable(key, value);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }
    }

    @Override
    public HttpRequestValues transform(InvocationContext invocation, HttpRequestValues requestValues) {
        final var annotatedPathVariables = conf.get(invocation.endpoint().method());
        if (annotatedPathVariables.isEmpty()) {
            return requestValues;
        }
        final var ectx = invocation.expressions().context(invocation);
        final var builder = HttpRequestValuesTransformer.valuesBuilder(requestValues);
        for (final var annotatedPathVariable : annotatedPathVariables) {

            builder.setUriVariable(
                    annotatedPathVariable.key().evaluate(ectx),
                    annotatedPathVariable.value().evaluate(ectx)
            );
        }
        return builder.build();
    }

}
