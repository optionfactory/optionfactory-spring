package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import net.optionfactory.spring.upstream.UpstreamResponseErrorHandler;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import net.optionfactory.spring.upstream.UpstreamAfterMappingHandler;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<InvocationContext> ictx = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> reqCtx = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> resCtx = new ThreadLocal<>();
    private final Class<?> klass;
    private final String upstreamId;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final Supplier<Object> principal;
    private final InstantSource clock;
    private final Map<Method, String> endpointNames;

    public ThreadLocalScopeHandler(Class<?> klass, String upstreamId, Supplier<Object> principal, InstantSource clock, Map<Method, String> endpointNames) {
        this.klass = klass;
        this.upstreamId = upstreamId;
        this.principal = principal;
        this.clock = clock;
        this.endpointNames = endpointNames;
    }

    @Override
    public MethodInterceptor interceptor(HttpMessageConverters converters, List<UpstreamAfterMappingHandler> afterMappingHandlers) {
        final var methodToPrincipalParamIndex = Stream.of(klass.getDeclaredMethods())
                .filter(m -> Stream.of(m.getParameters()).anyMatch(p -> p.isAnnotationPresent(Upstream.Principal.class)))
                .collect(Collectors.toConcurrentMap(m -> m, m -> {
                    final Parameter[] ps = m.getParameters();
                    for (int i = 0; i != ps.length; i++) {
                        final var p = ps[i];
                        if (p.isAnnotationPresent(Upstream.Principal.class)) {
                            return i;
                        }
                    }
                    throw new IllegalStateException("unreachable");
                }));

        return (MethodInvocation invocation) -> {
            final var method = invocation.getMethod();
            if (method.isDefault()) {
                if (invocation instanceof ReflectiveMethodInvocation rmi) {
                    return InvocationHandler.invokeDefault(rmi.getProxy(), method, invocation.getArguments());
                }
                throw new IllegalStateException("Unexpected method invocation: " + method);
            }

            //return ScopedValue.where(ctx, new UpstreamHttpInterceptor.InvocationContext(...).call(() -> {...});
            final var eprincipal = Optional.ofNullable(methodToPrincipalParamIndex.get(method))
                    .map(i -> invocation.getArguments()[i])
                    .or(() -> Optional.ofNullable(principal.get()))
                    .orElse(null);
            ictx.set(new InvocationContext(converters, upstreamId, endpointNames.get(method), method, invocation.getArguments(), BOOT_ID, eprincipal));
            try {
                final var result = invocation.proceed();
                for (UpstreamAfterMappingHandler amh : afterMappingHandlers) {
                    amh.handle(ictx.get(), reqCtx.get(), resCtx.get(), result);
                }
                return result;
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
