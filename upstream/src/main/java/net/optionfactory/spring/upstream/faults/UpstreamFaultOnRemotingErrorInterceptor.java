package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamFaultOnRemotingErrorInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, Expression> confs = new ConcurrentHashMap<>();
    private final Consumer<Object> publisher;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public UpstreamFaultOnRemotingErrorInterceptor(Consumer<Object> publisher) {
        this.publisher = publisher;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        for (Method m : k.getMethods()) {
            if(m.isSynthetic() || m.isBridge() || m.isDefault()){
                continue;
            }
            Annotations.closest(m, Upstream.FaultOnRemotingError.class)
                    .map(ann -> parser.parseExpression(ann.value()))
                    .ifPresent(expression -> confs.put(m, expression));
        }
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        try {
            return execution.execute(invocation, request);
        } catch (Exception exception) {
            final var expression = confs.get(invocation.method());
            if (expression == null) {
                throw exception;
            }
            final var exceptionContext = new ExceptionContext(Instant.now(), exception.getMessage());
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("invocation", invocation);
            ectx.setVariable("request", request);
            ectx.setVariable("exception", exceptionContext);
            if (expression.getValue(ectx, boolean.class)) {
                publisher.accept(new UpstreamFaultEvent(invocation, request, null, exceptionContext));
            }
            throw exception;
        }
    }

}
