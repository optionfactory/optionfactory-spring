package net.optionfactory.spring.upstream.errors;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamAfterMappingHandler;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamErrorAfterMappingHandler implements UpstreamAfterMappingHandler {

    private record Expressions(Expression predicate, Expression message) {

    }

    private final Map<Method, List<Expressions>> conf = new ConcurrentHashMap<>();
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateParserContext = new TemplateParserContext();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        for (Method m : k.getMethods()) {
            if(m.isSynthetic() || m.isBridge() || m.isDefault()){
                continue;
            }            
            final var anns = Annotations.closestRepeatable(m, Upstream.ErrorAfterMapping.class)
                    .stream()
                    .map(annotation -> {
                        final var predicate = parser.parseExpression(annotation.value());
                        final var message = parser.parseExpression(annotation.reason(), templateParserContext);
                        return new Expressions(predicate, message);
                    })
                    .toList();
            conf.put(m, anns);
        }
    }

    @Override
    public void handle(InvocationContext invocation, RequestContext request, ResponseContext response, Object result) {
        final List<Expressions> expressions = conf.get(invocation.method());
        if (expressions == null) {
            return;
        }
        for (Expressions expression : expressions) {
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("invocation", invocation);
            ectx.setVariable("response", response);
            ectx.setVariable("result", result);
            if (expression.predicate.getValue(ectx, boolean.class)) {
                final var reason = expression.message.getValue(ectx, String.class);
                throw new RestClientUpstreamException(
                        invocation.upstream(),
                        invocation.endpoint(),
                        reason,
                        response.status(),
                        response.statusText(),
                        response.headers(),
                        response.body().bytes()
                );
            }
        }
    }

}
