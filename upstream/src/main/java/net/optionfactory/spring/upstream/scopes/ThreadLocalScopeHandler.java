package net.optionfactory.spring.upstream.scopes;

import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;

public class ThreadLocalScopeHandler implements ScopeHandler {

    private final ThreadLocal<UpstreamHttpInterceptor.InvocationContext> ctx = new ThreadLocal<>();
    private final String upstreamId;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final Supplier<Object> principal;
    private final InstantSource clock;

    public ThreadLocalScopeHandler(String upstreamId, Supplier<Object> principal, InstantSource clock) {
        this.upstreamId = upstreamId;
        this.principal = principal;
        this.clock = clock;
    }

    @Override
    public MethodInterceptor interceptor(List<HttpMessageConverter<?>> converters) {
        return (MethodInvocation invocation) -> {
            //return ScopedValue.where(ctx, new UpstreamHttpInterceptor.InvocationContext(...).call(() -> {...});
            ctx.set(new UpstreamHttpInterceptor.InvocationContext(upstreamId, converters, clock, clock.instant(), invocation.getMethod(), invocation.getArguments(), BOOT_ID, principal.get(), requestCounter.incrementAndGet()));
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

}
