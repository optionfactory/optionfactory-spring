package net.optionfactory.spring.upstream.faults;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.Upstream.FaultAfterMapping;
import net.optionfactory.spring.upstream.UpstreamAfterMappingHandler;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamFaultAfterMappingHandler implements UpstreamAfterMappingHandler {

    private final Map<Method, Expression> confs = new ConcurrentHashMap<>();
    private final Consumer<Object> publisher;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public UpstreamFaultAfterMappingHandler(Consumer<Object> publisher) {
        this.publisher = publisher;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var interfaceOnRemotingError = Optional.ofNullable(k.getAnnotation(FaultAfterMapping.class));
        for (Method m : k.getMethods()) {
            if(m.isSynthetic() || m.isBridge() || m.isDefault()){
                continue;
            }
            Optional.ofNullable(m.getAnnotation(Upstream.FaultAfterMapping.class))
                    .or(() -> interfaceOnRemotingError)
                    .map(ann -> parser.parseExpression(ann.value()))
                    .ifPresent(expression -> confs.put(m, expression));
        }
    }

    @Override
    public void handle(InvocationContext invocation, RequestContext request, ResponseContext response, Object result) {
        final var expression = confs.get(invocation.method());
        if (expression == null) {
            return;
        }
        final var ectx = new StandardEvaluationContext();
        ectx.setVariable("invocation", invocation);
        ectx.setVariable("request", request);
        ectx.setVariable("response", response);
        ectx.setVariable("result", result);
        if (expression.getValue(ectx, boolean.class)) {
            publisher.accept(new UpstreamFaultEvent(invocation, request, response.detached(), null));
        }
    }

}
