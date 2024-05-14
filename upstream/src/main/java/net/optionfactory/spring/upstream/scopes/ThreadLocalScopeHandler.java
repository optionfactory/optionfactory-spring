package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> ictx = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> reqCtx = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> resCtx = new ThreadLocal<>();
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
                reqCtx.remove();
                resCtx.remove();
            }
        };
    }

    public ClientHttpRequestInterceptor requestContextInterceptor() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            reqCtx.set(new RequestContext(requestCounter.incrementAndGet(), clock.instant(), request.getMethod(), request.getURI(), request.getHeaders(), body));
            return execution.execute(request, body);
        };
    }

    public ClientHttpRequestInterceptor responseContextInterceptor(InstantSource clock) {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            final var response = execution.execute(request, body);
            resCtx.set(new ResponseContext(clock.instant(), response.getStatusCode(), response.getStatusText(), response.getHeaders(), BodySource.of(response)));
            return response;
        };
    }

    @Override
    public ClientHttpRequestInitializer adapt(UpstreamHttpRequestInitializer initializer) {
        return (request) -> initializer.initialize(ictx.get(), request);
    }

    @Override
    public ClientHttpRequestInterceptor adapt(UpstreamHttpInterceptor interceptor) {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            final var responseHolder = new AtomicReference<ClientHttpResponse>();
            interceptor.intercept(ictx.get(), reqCtx.get(), (InvocationContext ctx, RequestContext rctx) -> {
                reqCtx.set(rctx);
                final ClientHttpResponse chr = execution.execute(request, rctx.body());
                responseHolder.set(chr);
                return resCtx.get();
            });
            return responseHolder.get();
        };
    }

    @Override
    public ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory) {
        return (uri, httpMethod) -> factory.createRequest(ictx.get(), uri, httpMethod);

    }

    @Override
    public ResponseErrorHandler adapt(UpstreamResponseErrorHandler eh) {
        return new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return eh.hasError(ictx.get(), reqCtx.get(), resCtx.get());
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                eh.handleError(ictx.get(), reqCtx.get(), resCtx.get());
            }
        };
    }

}
