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
import net.optionfactory.spring.upstream.expressions.BooleanExpression;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import org.springframework.expression.EvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;

/// Turns the responses matching an `@Upstream.ErrorOnResponse` condition into a
/// [RestClientUpstreamException] carrying the reason of the annotation.
///
/// Always registered by [net.optionfactory.spring.upstream.UpstreamBuilder], as the last handler:
/// `4xx` and `5xx` responses are handled by [UpstreamErrorOnErrorStatusHandler] before reaching it.
///
/// The conditions are evaluated in [#hasError] and again in [#handleError], so they should not have
/// side effects.
public class UpstreamErrorOnResponseHandler implements UpstreamResponseErrorHandler {

    private record AnnotatedValues(Set<HttpStatus.Series> series, BooleanExpression predicate, StringExpression message) {

    }

    private final Map<Method, List<AnnotatedValues>> conf = new ConcurrentHashMap<>();

    /// Reads the `@Upstream.ErrorOnResponse` annotations of every endpoint, from the method or else from
    /// the interface hierarchy, and parses their conditions and reasons.
    ///
    /// @param k the proxied interface
    /// @param expressions the expressions of the client
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Annotations.closestRepeatable(endpoint.method(), k, Upstream.ErrorOnResponse.class)
                    .stream()
                    .map(annotation -> {
                        final var predicate = expressions.bool(annotation.value());
                        final var message = expressions.string(annotation.reason(), annotation.reasonType());
                        return new AnnotatedValues(Set.of(annotation.series()), predicate, message);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }
    }

    /// @param invocation the invocation
    /// @param request the request sent
    /// @param response the response received
    /// @return true when an annotation of the endpoint applies to the status series and its condition
    /// matches; false for the non-standard status codes (e.g. `999`), which belong to no series
    /// and are let through rather than failing the response handling
    @Override
    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var ectx = invocation.expressions().context(invocation, request, response);
        return firstMatching(ectx, invocation.endpoint().method(), response.status().value()).isPresent();
    }

    /// @param invocation the invocation
    /// @param request the request sent
    /// @param response the response received, whose body is read into the exception
    /// @throws RestClientUpstreamException always, with the reason of the first matching annotation
    @Override
    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var ectx = invocation.expressions().context(invocation, request, response);
        final var e = firstMatching(ectx, invocation.endpoint().method(), response.status().value()).orElseThrow().message;
        final String reason = e.evaluate(ectx);

        throw new RestClientUpstreamException(
                invocation.converters(),
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
        final Series serie = Series.resolve(statusCode);
        if (serie == null) {
            return Optional.empty();
        }
        for (AnnotatedValues expression : expressions) {
            if (!expression.series().contains(serie)) {
                continue;
            }
            if (expression.predicate.evaluate(ectx)) {
                return Optional.of(expression);
            }
        }
        return Optional.empty();
    }

}
