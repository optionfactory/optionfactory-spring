package net.optionfactory.spring.upstream.values;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter.HttpRequestValuesTransformer;
import org.springframework.web.service.invoker.HttpRequestValues;

/// Sets the uri variables declared by the endpoints' [Upstream.PathVariable] annotations, before
/// the uri is expanded.
///
/// Each annotation sets the variable named by the evaluated `key` (static by default) to the
/// evaluated `value` (an expression by default), in declaration order: a variable set by a later
/// annotation, or already bound to a `@PathVariable` parameter, is overwritten. The expressions see
/// `#upstream`, `#endpoint`, `#invocation`, `#args` and the method parameters by name; there is no
/// `#request` yet. The annotations are read from the method only.
///
/// ```java
/// @GetExchange("/users/{id}")
/// @Upstream.PathVariable(key = "id", value = "#user.id()")
/// Profile profile(@Upstream.Context User user);
/// ```
///
/// Installed on every client by `UpstreamBuilder`, ahead of the configured transformers. Endpoints
/// without the annotation get their request values untouched; the others get them rebuilt through
/// [HttpRequestValuesTransformer#valuesBuilder(HttpRequestValues)].
public class UpstreamAnnotatedPathVariableTransformer implements HttpRequestValuesTransformer {

    private final Map<Method, List<AnnotatedPathVariable>> conf = new ConcurrentHashMap<>();

    private record AnnotatedPathVariable(StringExpression key, StringExpression value) {

    }

    /// Compiles the [Upstream.PathVariable] annotations of every endpoint.
    ///
    /// @param k the client interface
    /// @param expressions the parser of the annotations' expressions
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Annotations.onMethodRepeatable(endpoint.method(), Upstream.PathVariable.class).stream()
                    .map(annotation -> {
                        final var key = expressions.string(annotation.key(), annotation.keyType());
                        final var value = expressions.string(annotation.value(), annotation.valueType());
                        return new AnnotatedPathVariable(key, value);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }
    }

    /// @param invocation the invocation in progress
    /// @param requestValues the request values
    /// @return `requestValues` itself when the endpoint has no annotation, new values with the
    /// variables set otherwise
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
