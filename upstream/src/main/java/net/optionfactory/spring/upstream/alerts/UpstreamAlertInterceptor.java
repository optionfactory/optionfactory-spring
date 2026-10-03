package net.optionfactory.spring.upstream.alerts;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.BooleanExpression;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/// Raises the alerts declared by `@Upstream.AlertOnResponse` and `@Upstream.AlertOnRemotingError`:
/// publishes an [UpstreamAlertEvent] and tags the current observation with `alert=response` or
/// `alert=remoting`.
///
/// Always registered by [net.optionfactory.spring.upstream.UpstreamBuilder], as the innermost
/// interceptor. Raising an alert does not change the outcome of the call: a matching response is
/// returned (flagged with [ResponseContext#withAlert()], so that it is not reported twice) and a
/// failure is rethrown.
///
/// Alerting never changes the outcome of the call either: a condition that fails to evaluate, or a
/// publisher that throws, is logged at `ERROR` with the upstream and endpoint, and no alert is
/// raised, while the response is returned, or the remoting error rethrown, as if alerting had not
/// been configured. Only the failures of the exchange are remoting errors.
public class UpstreamAlertInterceptor implements UpstreamHttpInterceptor {

    private final Logger logger = LoggerFactory.getLogger(UpstreamAlertInterceptor.class);

    private final Map<Method, BooleanExpression> remotingConfs = new ConcurrentHashMap<>();
    private final Map<Method, BooleanExpression> responseConfs = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher publisher;
    private final ObservationRegistry observations;
    private final InstantSource clock;

    /// @param publisher receives the alert events
    /// @param observations the registry whose current observation is tagged with the alert kind
    /// @param clock timestamps the remoting errors, see [ExceptionContext#at()]
    public UpstreamAlertInterceptor(ApplicationEventPublisher publisher, ObservationRegistry observations, InstantSource clock) {
        this.publisher = publisher;
        this.observations = observations;
        this.clock = clock;
    }

    /// Creates an interceptor timestamping the remoting errors with the system clock.
    ///
    /// @param publisher receives the alert events
    /// @param observations the registry whose current observation is tagged with the alert kind
    public UpstreamAlertInterceptor(ApplicationEventPublisher publisher, ObservationRegistry observations) {
        this(publisher, observations, InstantSource.system());
    }

    /// Reads the `@Upstream.AlertOnResponse` and `@Upstream.AlertOnRemotingError` of every endpoint,
    /// looked up on the method first and then on the interface hierarchy, and parses their conditions.
    ///
    /// @param k the proxied interface
    /// @param expressions the expressions of the client
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            Annotations.closest(endpoint.method(), k, Upstream.AlertOnRemotingError.class)
                    .map(ann -> expressions.bool(ann.value()))
                    .ifPresent(expression -> remotingConfs.put(endpoint.method(), expression));
            Annotations.closest(endpoint.method(), k, Upstream.AlertOnResponse.class)
                    .map(ann -> expressions.bool(ann.value()))
                    .ifPresent(expression -> responseConfs.put(endpoint.method(), expression));

        }
    }

    /// @param invocation the invocation in progress
    /// @param request the request to send
    /// @param execution the rest of the chain
    /// @return the response, flagged as alerted when it matched the endpoint condition
    /// @throws IOException the failure of the exchange, rethrown after evaluating the remoting condition
    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final ResponseContext response;
        try {
            response = execution.execute(invocation, request);
        } catch (Exception exception) {
            final var expression = remotingConfs.get(invocation.endpoint().method());
            if (expression != null) {
                final var exceptionContext = new ExceptionContext(clock.instant(), exception.getMessage());
                raised(invocation, () -> {
                    final var ectx = invocation.expressions().context(invocation, request, exceptionContext);
                    if (!expression.evaluate(ectx)) {
                        return false;
                    }
                    publish(invocation, request, null, exceptionContext);
                    return true;
                });
            }
            throw exception;
        }
        final var expression = responseConfs.get(invocation.endpoint().method());
        if (expression == null) {
            return response;
        }
        final boolean alerted = raised(invocation, () -> {
            final var ectx = invocation.expressions().context(invocation, request, response);
            if (!expression.evaluate(ectx)) {
                return false;
            }
            publish(invocation, request, response, null);
            return true;
        });
        return alerted ? response.withAlert() : response;
    }

    private boolean raised(InvocationContext invocation, BooleanSupplier alerting) {
        try {
            return alerting.getAsBoolean();
        } catch (RuntimeException ex) {
            logger.error(String.format("alerting failed for %s.%s, no alert raised", invocation.endpoint().upstream(), invocation.endpoint().name()), ex);
            return false;
        }
    }

    private void publish(InvocationContext invocation, RequestContext request, ResponseContext response, ExceptionContext ex) {
        Optional.ofNullable(observations.getCurrentObservation())
                .ifPresent(o -> {
                    o.lowCardinalityKeyValue("alert", ex == null ? "response" : "remoting");
                });

        publisher.publishEvent(new UpstreamAlertEvent(invocation, request, response == null ? null : response.detached(), ex));

    }
}
