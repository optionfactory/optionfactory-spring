package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import net.optionfactory.spring.upstream.Upstream.FaultOnRemotingError;
import net.optionfactory.spring.upstream.Upstream.FaultOnResponse;
import net.optionfactory.spring.upstream.paths.JsonPath;
import net.optionfactory.spring.upstream.paths.XmlPath;

public class UpstreamFaultInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, Expression> remotingResponseConfs = new ConcurrentHashMap<>();
    private final Map<Method, Expression> remotingErrorConfs = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher publisher;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public UpstreamFaultInterceptor(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var defaultOnResponse = AnnotationUtils.synthesizeAnnotation(Upstream.FaultOnResponse.class);
        final var defaultOnRemotingError = AnnotationUtils.synthesizeAnnotation(Upstream.FaultOnRemotingError.class);

        final var interfaceOnResponse = Optional.ofNullable(k.getAnnotation(FaultOnResponse.class));
        final var interfaceOnRemotingError = Optional.ofNullable(k.getAnnotation(FaultOnRemotingError.class));
        for (Method m : k.getDeclaredMethods()) {
            if (m.isDefault()) {
                continue;
            }
            final var onResponse = Optional.ofNullable(m.getAnnotation(Upstream.FaultOnResponse.class))
                    .or(() -> interfaceOnResponse)
                    .orElse(defaultOnResponse);
            remotingResponseConfs.put(m, parser.parseExpression(onResponse.value()));

            final var onRemotingError = Optional.ofNullable(m.getAnnotation(Upstream.FaultOnRemotingError.class))
                    .or(() -> interfaceOnRemotingError)
                    .orElse(defaultOnRemotingError);
            remotingErrorConfs.put(m, parser.parseExpression(onRemotingError.value()));
        }
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            final ClientHttpResponse response = execution.execute(request, body);
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("ctx", ctx);
            ectx.setVariable("request", request);
            ectx.setVariable("requestBody", body);
            ectx.setVariable("response", response);
            ectx.setVariable("status", response.getStatusCode());
            ectx.registerFunction("json_path", JsonPath.boundMethodHandle(ctx.converters(), response));
            ectx.registerFunction("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));

            if (remotingResponseConfs.get(ctx.method()).getValue(ectx, boolean.class)) {
                publisher.publishEvent(new UpstreamFaultEvent(ctx.boot(), ctx.upstream(), ctx.principal(), ctx.endpoint(), ctx.method(), ctx.arguments(), ctx.request(), ctx.requestedAt(), request.getURI(), request.getMethod(), request.getHeaders(), body, ctx.clock().instant(), response.getHeaders(), response.getStatusCode(), StreamUtils.copyToByteArray(response.getBody()), null));
            }
            return response;
        } catch (Exception ex) {
            final var ectx = new StandardEvaluationContext();
            ectx.setVariable("ctx", ctx);
            ectx.setVariable("request", request);
            ectx.setVariable("requestBody", body);
            ectx.setVariable("ex", ex);
            if (remotingErrorConfs.get(ctx.method()).getValue(ectx, boolean.class)) {
                publisher.publishEvent(new UpstreamFaultEvent(ctx.boot(), ctx.upstream(), ctx.principal(), ctx.endpoint(), ctx.method(), ctx.arguments(), ctx.request(), ctx.requestedAt(), request.getURI(), request.getMethod(), request.getHeaders(), body, ctx.clock().instant(), null, null, null, ex));
            }
            throw ex;
        }
    }

}
