package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.time.InstantSource;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class InterceptorChainAdapter implements ClientHttpRequestInterceptor {

    private final List<UpstreamHttpInterceptor> interceptors;
    private final Supplier<InvocationContext> invocation;
    private final AtomicLong requestCounter;
    private final InstantSource clock;

    public InterceptorChainAdapter(List<UpstreamHttpInterceptor> interceptors, Supplier<InvocationContext> invocation, AtomicLong requestCounter, InstantSource clock) {
        this.interceptors = interceptors;
        this.invocation = invocation;
        this.requestCounter = requestCounter;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        final AtomicReference<ClientHttpResponse> originalResponse = new AtomicReference<>();
        final InvocationContext ictx = invocation.get();
        final var chain = new InterceptorChain(interceptors.iterator(), execution, clock, originalResponse);
        final var reqCtx = new RequestContext(requestCounter.incrementAndGet(), clock.instant(), request.getMethod(), request.getURI(), request.getHeaders(), body);
        final var respCtx = chain.execute(ictx, reqCtx);
        return new ResponseAdapter(ictx, reqCtx, respCtx, originalResponse.get());
    }

    private static class InterceptorChain implements UpstreamHttpRequestExecution {

        private final Iterator<UpstreamHttpInterceptor> interceptors;
        private final ClientHttpRequestExecution execution;
        private final InstantSource clock;
        private final AtomicReference<ClientHttpResponse> originalResponse;

        public InterceptorChain(Iterator<UpstreamHttpInterceptor> interceptors, ClientHttpRequestExecution execution, InstantSource clock, AtomicReference<ClientHttpResponse> originalResponse) {
            this.interceptors = interceptors;
            this.execution = execution;
            this.clock = clock;
            this.originalResponse = originalResponse;
        }

        @Override
        public ResponseContext execute(InvocationContext invocation, RequestContext request) throws IOException {
            if (interceptors.hasNext()) {
                return interceptors.next().intercept(invocation, request, new InterceptorChain(interceptors, execution, clock, originalResponse));
            }
            final var response = this.execution.execute(new RequestAdapter(request), request.body());
            originalResponse.set(response);
            return new ResponseContext(
                    clock.instant(),
                    response.getStatusCode(),
                    response.getStatusText(),
                    response.getHeaders(),
                    BodySource.of(response)
            );
        }

    }

}
