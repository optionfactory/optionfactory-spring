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
import net.optionfactory.spring.upstream.expressions.Expressions.Type;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

public class UpstreamAnnotatedQueryParamsInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, List<AnnotatedValues>> conf = new ConcurrentHashMap<>();

    private record AnnotatedValues(BooleanExpression condition, StringExpression key, StringExpression value) {

    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Stream.of(endpoint.method().getAnnotationsByType(Upstream.QueryParam.class))
                    .map(annotation -> {
                        final var condition = expressions.bool(annotation.condition());
                        final var key = expressions.string(annotation.key(), annotation.keyType());
                        final var value = expressions.string(annotation.value(), annotation.valueType());
                        return new AnnotatedValues(condition, key, value);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }

    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final var aqps = conf.get(invocation.endpoint().method());

        final var ectx = invocation.expressions().context(invocation, request);

        final var queryParams = new LinkedMultiValueMap<String, String>();
        for (final var aqp : aqps) {
            if (!aqp.condition().evaluate(ectx)) {
                continue;
            }
            queryParams.add(
                    aqp.key().evaluate(ectx),
                    aqp.value().evaluate(ectx)
            );
        }
        if (!queryParams.isEmpty()) {
            final var newURi = UriComponentsBuilder.fromUri(request.uri())
                    .queryParams(queryParams)
                    .build()
                    .toUri();
            request = request.withUri(newURi);
        }
        return execution.execute(invocation, request);
    }

}
