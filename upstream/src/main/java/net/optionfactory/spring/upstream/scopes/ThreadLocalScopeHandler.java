package net.optionfactory.spring.upstream.scopes;

import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> invocations = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> requests = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> responses = new ThreadLocal<>();
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final Supplier<Object> principal;
    private final InstantSource clock;
    private final Map<Method, EndpointDescriptor> endpoints;
    private final Expressions expressions;
    private final ObservationRegistry observations;
    private final Consumer<Object> publisher;

    public ThreadLocalScopeHandler(Supplier<Object> principal, InstantSource clock, Map<Method, EndpointDescriptor> endpoints, Expressions expressions, ObservationRegistry observations, Consumer<Object> publisher) {
        this.principal = principal;
        this.clock = clock;
        this.endpoints = endpoints;
        this.expressions = expressions;
        this.observations = observations;
        this.publisher = publisher;
    }

    @Override
    public MethodInterceptor interceptor(HttpMessageConverters converters) {
        return new UpstreamethodInterceptor(endpoints, invocations, principal, expressions, converters, observations, requests::get, responses::get, clock, publisher);
    }

    @Override
    public ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer) {
        return new RequestInitializerAdapter(initializer, invocations::get);
    }

    @Override
    public ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors) {
        return new InterceptorChainAdapter(interceptors, invocations::get, requests::set, responses::set, requestCounter, clock);
    }

    @Override
    public ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory) {
        return new RequestFactoryAdapter(factory, invocations::get);

    }

    @Override
    public ResponseErrorHandler adapt(UpstreamResponseErrorHandler eh) {
        return new ResponseErrorHandlerAdapter(eh);
    }

}
