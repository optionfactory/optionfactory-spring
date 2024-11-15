package net.optionfactory.spring.upstream.scopes;

import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.service.invoker.HttpExchangeAdapter;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> invocations = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> requests = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> responses = new ThreadLocal<>();
    private final Supplier<Object> principal;
    private final InstantSource clock;
    private final Map<Method, EndpointDescriptor> endpoints;
    private final Expressions expressions;
    private final ObservationRegistry observations;
    private final ApplicationEventPublisher publisher;

    public ThreadLocalScopeHandler(Supplier<Object> principal, InstantSource clock, Map<Method, EndpointDescriptor> endpoints, Expressions expressions, ObservationRegistry observations, ApplicationEventPublisher publisher) {
        this.principal = principal;
        this.clock = clock;
        this.endpoints = endpoints;
        this.expressions = expressions;
        this.observations = observations;
        this.publisher = publisher;
    }

    @Override
    public HttpExchangeAdapter adapt(UpstreamHttpExchangeAdapter adapter) {
        return new UpstreamHttpExchangeAdapterAdapter(adapter, invocations::get);
    }

    @Override
    public MethodInterceptor interceptor(HttpMessageConverters converters) {
        return new UpstreamMethodInterceptor(endpoints, invocations, principal, expressions, converters, observations, requests, responses, clock, publisher);
    }

    @Override
    public ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer) {
        return new RequestInitializerAdapter(initializer, invocations::get);
    }

    @Override
    public ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors) {
        return new InterceptorChainAdapter(interceptors, invocations::get, requests::set, responses::set, clock);
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
