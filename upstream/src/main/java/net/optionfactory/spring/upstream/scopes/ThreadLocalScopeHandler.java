package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.scopes.ResponseErrorHandlerAdapter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> ictx = new ThreadLocal<>();
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final Supplier<Object> principal;
    private final InstantSource clock;
    private final Map<Method, EndpointDescriptor> endpoints;

    public ThreadLocalScopeHandler(Supplier<Object> principal, InstantSource clock, Map<Method, EndpointDescriptor> endpoints) {
        this.principal = principal;
        this.clock = clock;
        this.endpoints = endpoints;
    }

    @Override
    public MethodInterceptor interceptor(HttpMessageConverters converters) {

        return (MethodInvocation invocation) -> {
            final var method = invocation.getMethod();
            if (method.isDefault()) {
                if (invocation instanceof ReflectiveMethodInvocation rmi) {
                    return InvocationHandler.invokeDefault(rmi.getProxy(), method, invocation.getArguments());
                }
                throw new IllegalStateException("Unexpected method invocation: " + method);
            }
            final var endpoint = endpoints.get(method);
            //return ScopedValue.where(ctx, new UpstreamHttpInterceptor.InvocationContext(...).call(() -> {...});
            final var eprincipal = Optional.ofNullable(endpoint.principalParamIndex())
                    .map(i -> invocation.getArguments()[i])
                    .or(() -> Optional.ofNullable(principal.get()))
                    .orElse(null);
            ictx.set(new InvocationContext(converters, endpoint, invocation.getArguments(), BOOT_ID, eprincipal));
            try {
                return invocation.proceed();
            } finally {
                ictx.remove();
            }
        };
    }

    @Override
    public ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer) {
        return new RequestInitializerAdapter(initializer, ictx::get);
    }

    @Override
    public ClientHttpRequestInterceptor adapt(List<UpstreamHttpInterceptor> interceptors) {
        return new InterceptorChainAdapter(interceptors, ictx::get, requestCounter, clock);
    }

    @Override
    public ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory) {
        return new RequestFactoryAdapter(factory, ictx::get);

    }

    @Override
    public ResponseErrorHandler adapt(UpstreamResponseErrorHandler eh) {
        return new ResponseErrorHandlerAdapter(eh);
    }

}
