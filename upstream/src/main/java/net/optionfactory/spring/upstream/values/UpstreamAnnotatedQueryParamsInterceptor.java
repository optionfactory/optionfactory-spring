package net.optionfactory.spring.upstream.values;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
import net.optionfactory.spring.upstream.expressions.BooleanExpression;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.StringExpression;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/// Appends the query parameters declared by the endpoints' [Upstream.QueryParam] annotations to
/// their request uris.
///
/// Each annotation whose `condition` holds appends the evaluated `key` (a template by default)
/// and `value` (an expression by default) to the query, after the existing parameters and in
/// declaration order. The expressions see `#upstream`, `#endpoint`, `#invocation`, `#request`, `#args` and the method parameters by
/// name. The annotations are read from the method only.
///
/// The request uri reaching the interceptors is already encoded, so its query is kept as it is,
/// and only the appended key and value are encoded, once: `a b&c` becomes `a%20b%26c`.
///
/// ```java
/// @GetExchange("/search")
/// @Upstream.QueryParam(key = "lang", value = "#locale.language", condition = "#locale != null")
/// Results search(@RequestParam String q, @Upstream.Context Locale locale);
/// ```
///
/// Installed on every client by `UpstreamBuilder`.
public class UpstreamAnnotatedQueryParamsInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, List<AnnotatedValues>> conf = new ConcurrentHashMap<>();

    private record AnnotatedValues(BooleanExpression condition, StringExpression key, StringExpression value) {

    }

    /// Compiles the [Upstream.QueryParam] annotations of every endpoint.
    ///
    /// @param k the client interface
    /// @param expressions the parser of the annotations' expressions
    /// @param endpoints the endpoints of the client, by method
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

    /// @param invocation the invocation in progress
    /// @param request the request
    /// @param execution the rest of the chain, which receives the request with the extended uri,
    /// or the same request when no parameter is appended
    /// @return the response of the rest of the chain
    /// @throws IOException when the rest of the chain fails
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
                    UriUtils.encodeQueryParam(aqp.key().evaluate(ectx), StandardCharsets.UTF_8),
                    UriUtils.encodeQueryParam(aqp.value().evaluate(ectx), StandardCharsets.UTF_8)
            );
        }
        if (!queryParams.isEmpty()) {
            final var newUri = UriComponentsBuilder.fromUri(request.uri())
                    .queryParams(queryParams)
                    .build(true)
                    .toUri();
            request = request.withUri(newUri);
        }
        return execution.execute(invocation, request);
    }

}
