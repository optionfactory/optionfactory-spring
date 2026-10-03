package net.optionfactory.spring.upstream.values;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.BooleanExpression;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;

/// Adds the cookies declared by the endpoints' [Upstream.Cookie] annotations to their requests.
///
/// Each annotation whose `condition` holds adds its evaluated `value` (a template by default),
/// expected in the `name=value` form, as a further `Cookie` header, in declaration order. The
/// expressions see `#upstream`, `#endpoint`, `#invocation`, `#request`, `#args` and the method parameters by
/// name. The annotations are read from the method only.
///
/// ```java
/// @GetExchange("/profile")
/// @Upstream.Cookie(value = "session=#{#session}", condition = "#session != null")
/// Profile profile(@Upstream.Context String session);
/// ```
///
/// Installed on every client by `UpstreamBuilder`.
public class UpstreamAnnotatedCookiesInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, List<AnnotatedCookie>> conf = new ConcurrentHashMap<>();

    private record AnnotatedCookie(BooleanExpression condition, StringExpression value) {

    }

    /// Compiles the [Upstream.Cookie] annotations of every endpoint.
    ///
    /// @param k the client interface
    /// @param expressions the parser of the annotations' expressions
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            final var anns = Annotations.onMethodRepeatable(endpoint.method(), Upstream.Cookie.class).stream()
                    .map(annotation -> {
                        final var condition = expressions.bool(annotation.condition());
                        final var value = expressions.string(annotation.value(), annotation.valueType());
                        return new AnnotatedCookie(condition, value);
                    })
                    .toList();
            conf.put(endpoint.method(), anns);
        }

    }

    /// @param invocation the invocation in progress
    /// @param request the request, whose headers receive the cookies
    /// @param execution the rest of the chain
    /// @return the response of the rest of the chain
    /// @throws IOException when the rest of the chain fails
    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final var annotatedCookies = conf.get(invocation.endpoint().method());
        final var ectx = invocation.expressions().context(invocation, request);
        for (final var annotatedCookie : annotatedCookies) {
            if (!annotatedCookie.condition().evaluate(ectx)) {
                continue;
            }
            request.headers().add("Cookie", annotatedCookie.value().evaluate(ectx));
        }
        return execution.execute(invocation, request);
    }

}
