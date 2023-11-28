package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.paths.JsonPath;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.paths.XmlPath;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamErrorOnReponseHandler implements UpstreamResponseErrorHandler {

    private record Expressions(Set<HttpStatus.Series> series, Expression predicate, Expression message) {

    }

    private final Map<Method, List<Expressions>> conf = new ConcurrentHashMap<>();
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateParserContext = new TemplateParserContext();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        for (Method m : k.getDeclaredMethods()) {
            if (m.isDefault()) {
                continue;
            }
            final var anns = Stream.of(m.getAnnotationsByType(Upstream.ErrorOnResponse.class))
                    .map(annotation -> {
                        final var predicate = parser.parseExpression(annotation.value());
                        final var message = parser.parseExpression(annotation.reason(), templateParserContext);
                        return new Expressions(Set.of(annotation.series()), predicate, message);
                    })
                    .toList();
            conf.put(m, anns);
        }
    }

    @Override
    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        return firstMatching(invocation, request, response).isPresent();
    }

    @Override
    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final var e = firstMatching(invocation, request, response).orElseThrow().message;
        final var ectx = new StandardEvaluationContext();
        ectx.setVariable("invocation", invocation);
        ectx.setVariable("request", request);
        ectx.setVariable("response", response);
        final String reason = e.getValue(ectx, String.class);

        throw new RestClientUpstreamException(
                invocation.upstream(),
                invocation.endpoint(),
                reason,
                response.status(),
                response.statusText(),
                response.headers(),
                response.body()
        );
    }


    private Optional<Expressions> firstMatching(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException {
        final List<Expressions> expressions = conf.get(invocation.method());
        if (expressions == null) {
            return Optional.empty();
        }
        for (Expressions expression : expressions) {
            final Series serie = Series.valueOf(response.status().value());
            if (!expression.series().contains(serie)) {
                continue;
            }
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("invocation", invocation);
            ectx.setVariable("request", request);
            ectx.setVariable("response", response);
            ectx.registerFunction("json_path", JsonPath.boundMethodHandle(invocation.converters(), response));
            ectx.registerFunction("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));
            if (expression.predicate.getValue(ectx, boolean.class)) {
                return Optional.of(expression);
            }
        }
        return Optional.empty();
    }

}
