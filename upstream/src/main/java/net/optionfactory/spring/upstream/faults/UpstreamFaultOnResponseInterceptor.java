package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.Upstream.FaultOnResponse;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.paths.JsonPath;
import net.optionfactory.spring.upstream.paths.XmlPath;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamFaultOnResponseInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, Expression> conf = new ConcurrentHashMap<>();
    private final Consumer<Object> publisher;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public UpstreamFaultOnResponseInterceptor(Consumer<Object> publisher) {
        this.publisher = publisher;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var interfaceOnResponse = Optional.ofNullable(k.getAnnotation(FaultOnResponse.class));
        for (Method m : k.getMethods()) {
            if(m.isSynthetic() || m.isBridge() || m.isDefault()){
                continue;
            }
            Optional.ofNullable(m.getAnnotation(Upstream.FaultOnResponse.class))
                    .or(() -> interfaceOnResponse)
                    .map(ann -> parser.parseExpression(ann.value()))
                    .ifPresent(expression -> conf.put(m, expression));
        }
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final ResponseContext response = execution.execute(invocation, request);
        final var expression = conf.get(invocation.method());
        if (expression == null) {
            return response;
        }
        final var ectx = new StandardEvaluationContext();
        ectx.setVariable("invocation", invocation);
        ectx.setVariable("request", request);
        ectx.setVariable("response", response);
        ectx.registerFunction("json_path", JsonPath.boundMethodHandle(invocation.converters(), response));
        ectx.registerFunction("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));

        if (expression.getValue(ectx, boolean.class)) {
            publisher.accept(new UpstreamFaultEvent(invocation, request, response.detached(), null));
        }
        return response;
    }

}
