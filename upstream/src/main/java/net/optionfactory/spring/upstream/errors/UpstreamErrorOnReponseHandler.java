package net.optionfactory.spring.upstream.errors;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;

public class UpstreamErrorOnReponseHandler implements UpstreamResponseErrorHandler {

    private record AnnotatedValues(Set<HttpStatus.Series> series, Expression predicate, Expression message) {

    }

    private final Map<Method, List<AnnotatedValues>> conf = new ConcurrentHashMap<>();

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Annotations.closestRepeatable(endpoint.method(), Upstream.ErrorOnResponse.class)
                    .stream()
                    .map(annotation -> {
                        final var predicate = expressions.parse(annotation.value());
                        final var message = expressions.parseTemplated(annotation.reason());
                        return new AnnotatedValues(Set.of(annotation.series()), predicate, message);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }
    }

    @Override
    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var ectx = invocation.expressions().context(invocation, request, response);
        return firstMatching(ectx, invocation.endpoint().method(), response.status().value()).isPresent();
    }

    @Override
    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var ectx = invocation.expressions().context(invocation, request, response);
        final var e = firstMatching(ectx, invocation.endpoint().method(), response.status().value()).orElseThrow().message;
        final String reason = e.getValue(ectx, String.class);

        throw new RestClientUpstreamException(
                invocation.endpoint().upstream(),
                invocation.endpoint().name(),
                reason,
                response.status(),
                response.statusText(),
                response.headers(),
                response.body().bytes()
        );
    }

    private Optional<AnnotatedValues> firstMatching(EvaluationContext ectx, Method m, int statusCode) throws IOException {
        final List<AnnotatedValues> expressions = conf.get(m);
        if (expressions == null) {
            return Optional.empty();
        }
        for (AnnotatedValues expression : expressions) {
            final Series serie = Series.valueOf(statusCode);
            if (!expression.series().contains(serie)) {
                continue;
            }
            if (expression.predicate.getValue(ectx, boolean.class)) {
                return Optional.of(expression);
            }
        }
        return Optional.empty();
    }

}
