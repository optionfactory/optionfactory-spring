package net.optionfactory.spring.upstream.scopes;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.faults.UpstreamFaultEvent;
import static net.optionfactory.spring.upstream.scopes.ScopeHandler.BOOT_ID;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

public class UpstreamethodInterceptor implements MethodInterceptor {

    private final Map<Method, EndpointDescriptor> endpoints;
    private final ThreadLocal<InvocationContext> invocations;
    private final Supplier<Object> principal;
    private final Expressions expressions;
    private final HttpMessageConverters converters;
    private final ObservationRegistry observations;
    private final Supplier<RequestContext> requests;
    private final Supplier<ResponseContext> responses;
    private final InstantSource clock;
    private final Consumer<Object> publisher;

    public UpstreamethodInterceptor(Map<Method, EndpointDescriptor> endpoints, ThreadLocal<InvocationContext> invocations, Supplier<Object> principal, Expressions expressions, HttpMessageConverters converters, ObservationRegistry observations,
            Supplier<RequestContext> requests,
            Supplier<ResponseContext> responses,
            InstantSource clock,
            Consumer<Object> publisher) {
        this.endpoints = endpoints;
        this.invocations = invocations;
        this.principal = principal;
        this.expressions = expressions;
        this.converters = converters;
        this.observations = observations;
        this.requests = requests;
        this.responses = responses;
        this.clock = clock;
        this.publisher = publisher;
    }

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
        //return ScopedValue.where(ctx, new UpstreamHttpInterceptor.InvocationContext(...).call(() -> {...});
        final var eprincipal = Optional.ofNullable(endpoint.principalParamIndex())
                .map(i -> mi.getArguments()[i])
                .or(() -> Optional.ofNullable(principal.get()))
                .orElse(null);
        final InvocationContext invocation = new InvocationContext(expressions, converters, endpoint, mi.getArguments(), BOOT_ID, eprincipal);
        invocations.set(invocation);
        final var obs = Observation.createNotStarted("upstream", observations)
                .lowCardinalityKeyValue("upstream", invocation.endpoint().upstream())
                .lowCardinalityKeyValue("endpoint", invocation.endpoint().name())
                .lowCardinalityKeyValue("fault", "none")
                .start();
        try (final var scope = obs.openScope()) {
            try {
                return mi.proceed();
            } catch (RestClientException ex) {
                reportFaults(ex, invocation, scope);
                throw ex;
            }
        } catch (Exception ex) {
            obs.error(ex);
            throw ex;
        } finally {
            obs.stop();
            invocations.remove();
        }

    }

    private void reportFaults(RestClientException ex, InvocationContext invocation, Scope scope) {

        if (ex.getCause() instanceof HttpMessageNotReadableException == false && ex instanceof UnknownContentTypeException == false) {
            return;
        }
        final ResponseContext response = responses.get();
        if (response != null && response.faulted()) {
            //already reported
            return;
        }
        final Observation obs = scope.getCurrentObservation();
        if ("none".equals(obs.getContext().getLowCardinalityKeyValue("fault").getValue())) {
            obs.lowCardinalityKeyValue("fault", "mapping");
        }
        final var request = requests.get();
        final var exc = new ExceptionContext(clock.instant(), ex.getMessage());
        publisher.accept(new UpstreamFaultEvent(invocation, request, response == null ? null : response.detached(), exc));

    }
}
