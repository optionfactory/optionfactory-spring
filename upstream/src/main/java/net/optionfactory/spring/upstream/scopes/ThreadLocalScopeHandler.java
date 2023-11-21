package net.optionfactory.spring.upstream.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import static org.springframework.http.RequestEntity.method;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<UpstreamHttpInterceptor.InvocationContext> ctx = new ThreadLocal<>();
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
    public MethodInterceptor interceptor(List<HttpMessageConverter<?>> converters) {
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
            ctx.set(new UpstreamHttpInterceptor.InvocationContext(upstreamId, converters, clock, clock.instant(), endpointNames.get(method), method, invocation.getArguments(), BOOT_ID, eprincipal, requestCounter.incrementAndGet()));
            try {
                return invocation.proceed();
            } finally {
                ctx.remove();
            }
        };
    }

    @Override
    public ClientHttpRequestInterceptor adapt(UpstreamHttpInterceptor interceptor) {
        return (request, body, execution) -> interceptor.intercept(ctx.get(), request, body, execution);
    }

    @Override
    public ClientHttpRequestFactory adapt(UpstreamHttpRequestFactory factory) {
        return (uri, httpMethod) -> factory.createRequest(ctx.get(), uri, httpMethod);

    }

}
