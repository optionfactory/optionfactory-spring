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
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.service.invoker.HttpExchangeAdapter;

/// A [ScopeHandler] keeping the invocation in progress, and its last request and response, in
/// thread locals.
///
/// The [UpstreamMethodInterceptor] sets them when a client method is called and removes them when
/// it returns, and the adapted components read them; this works because the `RestClient` runs the
/// whole exchange on the calling thread. Each client gets its own handler, and thread locals.
public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> invocations = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> requests = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> responses = new ThreadLocal<>();
    private final Supplier<Object> principal;
    private final InstantSource clock;
    private final Map<Method, EndpointDescriptor> endpoints;
    private final Expressions expressions;
    private final PayloadsRendering rendering;
    private final ObservationRegistry observations;
    private final ApplicationEventPublisher publisher;

    /// @param principal supplies the principal of invocations whose endpoint has no principal
    /// parameter
    /// @param clock timestamps requests, responses and alerts
    /// @param endpoints the endpoints of the client, by method
    /// @param expressions exposed by the invocation contexts
    /// @param rendering exposed by the invocation contexts
    /// @param observations the registry of the invocation observations
    /// @param publisher publishes the alerts on response mapping failures
    public ThreadLocalScopeHandler(Supplier<Object> principal, InstantSource clock, Map<Method, EndpointDescriptor> endpoints, Expressions expressions, PayloadsRendering rendering, ObservationRegistry observations, ApplicationEventPublisher publisher) {
        this.principal = principal;
        this.clock = clock;
        this.endpoints = endpoints;
        this.expressions = expressions;
        this.rendering = rendering;
        this.observations = observations;
        this.publisher = publisher;
    }

    /// @param adapter the exchange adapter
    /// @return an [UpstreamHttpExchangeAdapterAdapter] reading the thread-local invocation
    @Override
    public HttpExchangeAdapter adapt(UpstreamHttpExchangeAdapter adapter) {
        return new UpstreamHttpExchangeAdapterAdapter(adapter, invocations::get);
    }

    /// @param converters the client's message converters
    /// @return an [UpstreamMethodInterceptor] managing this handler's thread locals
    @Override
    public MethodInterceptor interceptor(MessageConverters converters) {
        return new UpstreamMethodInterceptor(endpoints, invocations, principal, expressions, rendering, converters, observations, requests, responses, clock, publisher);
    }

    /// @param initializer the request initializer
    /// @return a [RequestInitializerAdapter] reading the thread-local invocation
    @Override
    public ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer) {
        return new RequestInitializerAdapter(initializer, invocations::get);
    }

    /// @param interceptors the interceptors, in execution order
    /// @return an [InterceptorChainAdapter] reading the thread-local invocation and recording the
    /// request and response in the thread locals
    @Override
    public ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors) {
        return new InterceptorChainAdapter(interceptors, invocations::get, requests::set, responses::set, clock);
    }

    /// @param factory the request factory
    /// @return a [RequestFactoryAdapter] reading the thread-local invocation
    @Override
    public ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory) {
        return new RequestFactoryAdapter(factory, invocations::get);
    }

    /// @param eh the response error handler
    /// @return a [ResponseErrorHandlerAdapter], which reads the contexts from the response
    @Override
    public ResponseErrorHandler adapt(UpstreamResponseErrorHandler eh) {
        return new ResponseErrorHandlerAdapter(eh);
    }

}
