package net.optionfactory.spring.upstream.scopes;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import static net.optionfactory.spring.upstream.scopes.ScopeHandler.BOOT_ID;
import static net.optionfactory.spring.upstream.scopes.ScopeHandler.INVOCATION_COUNTER;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

/// The advice around every call to an upstream client, opening the invocation scope.
///
/// For each call to an endpoint it:
///
/// - creates the [InvocationContext], with a new id from [ScopeHandler#INVOCATION_COUNTER]; the
///   principal is the argument of the endpoint's `@Upstream.Principal` parameter when that is not
///   `null`, the principal supplier's otherwise; the response buffering follows the return type
///   (a `Stream` or an `InputStream` is streamed);
/// - makes it the invocation in progress in the thread local, and removes it, together with the
///   recorded request and response, when the call ends, however it ends;
/// - wraps the call in an `upstream` observation, tagged with the `upstream` and `endpoint` names
///   and an `alert` tag (`none` unless an alert is raised), recording the error that ends it;
/// - when the response cannot be mapped (a `RestClientException` caused by an
///   `HttpMessageNotReadableException`, or an `UnknownContentTypeException`) and no alert was
///   raised for the response already, tags the observation `alert=mapping` and publishes an
///   [UpstreamAlertEvent], then rethrows.
///
/// Default methods of the client interface are invoked directly, outside of any scope. Any other
/// method that is not an endpoint, `toString()` included, fails with a `NullPointerException`.
public class UpstreamMethodInterceptor implements MethodInterceptor {

    private final Map<Method, EndpointDescriptor> endpoints;
    private final ThreadLocal<InvocationContext> invocations;
    private final ThreadLocal<RequestContext> requests;
    private final ThreadLocal<ResponseContext> responses;
    private final Supplier<Object> principal;
    private final Expressions expressions;
    private final PayloadsRendering rendering;
    private final MessageConverters converters;
    private final ObservationRegistry observations;
    private final InstantSource clock;
    private final ApplicationEventPublisher publisher;

    /// @param endpoints the endpoints of the client, by method
    /// @param invocations holds the invocation in progress
    /// @param principal supplies the principal of invocations without a principal argument
    /// @param expressions exposed by the invocation contexts
    /// @param rendering exposed by the invocation contexts
    /// @param converters exposed by the invocation contexts
    /// @param observations the registry of the invocation observations
    /// @param requests holds the last request executed, cleared at the end of the call
    /// @param responses holds the last response received, cleared at the end of the call
    /// @param clock timestamps the alerts
    /// @param publisher publishes the alerts on response mapping failures
    public UpstreamMethodInterceptor(Map<Method, EndpointDescriptor> endpoints, ThreadLocal<InvocationContext> invocations, Supplier<Object> principal, Expressions expressions, PayloadsRendering rendering, MessageConverters converters, ObservationRegistry observations,
            ThreadLocal<RequestContext> requests,
            ThreadLocal<ResponseContext> responses,
            InstantSource clock,
            ApplicationEventPublisher publisher) {
        this.endpoints = endpoints;
        this.invocations = invocations;
        this.principal = principal;
        this.expressions = expressions;
        this.rendering = rendering;
        this.converters = converters;
        this.observations = observations;
        this.requests = requests;
        this.responses = responses;
        this.clock = clock;
        this.publisher = publisher;
    }

    /// @param mi the call to the client
    /// @return the result of the call
    /// @throws Throwable whatever the call throws
    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        final var method = mi.getMethod();
        if (method.isDefault()) {
            if (mi instanceof ReflectiveMethodInvocation rmi) {
                return InvocationHandler.invokeDefault(rmi.getProxy(), method, mi.getArguments());
            }
            throw new IllegalStateException("Unexpected method invocation: " + method);
        }
        final var endpoint = endpoints.get(method);
        final var eprincipal = Optional.ofNullable(endpoint.principalParamIndex())
                .map(i -> mi.getArguments()[i])
                .or(() -> Optional.ofNullable(principal.get()))
                .orElse(null);
        final var buffering = Buffering.responseBufferingFromMethod(method);
        final InvocationContext invocation = new InvocationContext(expressions, rendering, converters, endpoint, mi.getArguments(), BOOT_ID, INVOCATION_COUNTER.incrementAndGet(), eprincipal, buffering);
        invocations.set(invocation);
        final var obs = Observation.createNotStarted("upstream", observations)
                .lowCardinalityKeyValue("upstream", invocation.endpoint().upstream())
                .lowCardinalityKeyValue("endpoint", invocation.endpoint().name())
                .lowCardinalityKeyValue("alert", "none")
                .start();
        try (final var scope = obs.openScope()) {
            try {
                return mi.proceed();
            } catch (RestClientException ex) {
                reportMappingAlerts(ex, invocation, scope);
                throw ex;
            }
        } catch (Exception ex) {
            obs.error(ex);
            throw ex;
        } finally {
            obs.stop();
            responses.remove();
            requests.remove();
            invocations.remove();
        }

    }

    private void reportMappingAlerts(RestClientException ex, InvocationContext invocation, Scope scope) {

        if (ex.getCause() instanceof HttpMessageNotReadableException == false && ex instanceof UnknownContentTypeException == false) {
            return;
        }
        final ResponseContext response = responses.get();
        if (response != null && response.alert()) {
            return;
        }
        final var obs = scope.getCurrentObservation();
        final var kv = obs.getContext().getLowCardinalityKeyValue("alert");
        if (kv != null && "none".equals(kv.getValue())) {
            obs.lowCardinalityKeyValue("alert", "mapping");
        }
        final var request = requests.get();
        final var exc = new ExceptionContext(clock.instant(), ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        publisher.publishEvent(new UpstreamAlertEvent(invocation, request, response == null ? null : response.detached(), exc));

    }
}
