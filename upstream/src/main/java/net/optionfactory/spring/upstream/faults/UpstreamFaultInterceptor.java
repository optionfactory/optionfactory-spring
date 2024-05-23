package net.optionfactory.spring.upstream.faults;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.expression.Expression;

public class UpstreamFaultInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, Expression> remotingConfs = new ConcurrentHashMap<>();
    private final Map<Method, Expression> responseConfs = new ConcurrentHashMap<>();
    private final Consumer<Object> publisher;
    private final ObservationRegistry observations;

    public UpstreamFaultInterceptor(Consumer<Object> publisher, ObservationRegistry observations) {
        this.publisher = publisher;
        this.observations = observations;
    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            Annotations.closest(endpoint.method(), Upstream.FaultOnRemotingError.class)
                    .map(ann -> expressions.parse(ann.value()))
                    .ifPresent(expression -> remotingConfs.put(endpoint.method(), expression));
            Annotations.closest(endpoint.method(), Upstream.FaultOnResponse.class)
                    .map(ann -> expressions.parse(ann.value()))
                    .ifPresent(expression -> responseConfs.put(endpoint.method(), expression));

        }
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        try {
            final var response = execution.execute(invocation, request);
            final var expression = responseConfs.get(invocation.endpoint().method());
            if (expression == null) {
                return response;
            }
            final var ectx = invocation.expressions().context(invocation, request, response);
            if (expression.getValue(ectx, boolean.class)) {
                publish(invocation, request, response, null);
            }
            return response;

        } catch (Exception exception) {
            final var expression = remotingConfs.get(invocation.endpoint().method());
            if (expression == null) {
                throw exception;
            }
            final var exceptionContext = new ExceptionContext(Instant.now(), exception.getMessage());
            final var ectx = invocation.expressions().context(invocation, request, exceptionContext);
            if (expression.getValue(ectx, boolean.class)) {
                publish(invocation, request, null, exceptionContext);
            }
            throw exception;
        }
    }

    private void publish(InvocationContext invocation, RequestContext request, ResponseContext response, ExceptionContext ex) {
        Optional.ofNullable(observations.getCurrentObservation())
                .ifPresent(o -> {
                    o.lowCardinalityKeyValue("fault", ex == null ? "response" : "remoting");
                    //maybe we could generate an event here o.event(Event.of("fault", "my fault contextual name"));
                });

        publisher.accept(new UpstreamFaultEvent(invocation, request, response == null ? null : response.detached(), ex));

    }
}
