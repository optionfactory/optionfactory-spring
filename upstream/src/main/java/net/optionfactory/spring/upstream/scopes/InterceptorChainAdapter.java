package net.optionfactory.spring.upstream.scopes;

import java.io.IOException;
import java.time.InstantSource;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
    private final Supplier<InvocationContext> invocations;
    private final Consumer<RequestContext> requests;
    private final Consumer<ResponseContext> responses;
    private final InstantSource clock;

    public InterceptorChainAdapter(
            List<UpstreamHttpInterceptor> interceptors,
            Supplier<InvocationContext> invocations,
            Consumer<RequestContext> requests,
            Consumer<ResponseContext> responses,
            InstantSource clock) {
        this.interceptors = interceptors;
        this.invocations = invocations;
        this.requests = requests;
        this.responses = responses;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        requests.accept(null);
        responses.accept(null);
        final AtomicReference<ClientHttpResponse> originalResponse = new AtomicReference<>();
        final InvocationContext ictx = invocations.get();
        final var chain = new InterceptorChain(interceptors.iterator(), execution, clock, requests, originalResponse);
        final var reqCtx = new RequestContext(clock.instant(), request.getMethod(), request.getURI(), request.getHeaders(), body);
        final var respCtx = chain.execute(ictx, reqCtx);
        responses.accept(respCtx);
        return new ResponseAdapter(ictx, reqCtx, respCtx, originalResponse.get());
    }

    private static class InterceptorChain implements UpstreamHttpRequestExecution {

        private final Iterator<UpstreamHttpInterceptor> interceptors;
        private final ClientHttpRequestExecution execution;
        private final InstantSource clock;
        private final Consumer<RequestContext> requests;
        private final AtomicReference<ClientHttpResponse> originalResponse;

        public InterceptorChain(Iterator<UpstreamHttpInterceptor> interceptors, ClientHttpRequestExecution execution, InstantSource clock, Consumer<RequestContext> requests, AtomicReference<ClientHttpResponse> originalResponse) {
            this.interceptors = interceptors;
            this.execution = execution;
            this.clock = clock;
            this.requests = requests;
            this.originalResponse = originalResponse;
        }

        @Override
        public ResponseContext execute(InvocationContext invocation, RequestContext request) throws IOException {
            if (interceptors.hasNext()) {
                return interceptors.next().intercept(invocation, request, new InterceptorChain(interceptors, execution, clock, requests, originalResponse));
            }
            requests.accept(request);
            final var response = this.execution.execute(new RequestAdapter(request), request.body());
            originalResponse.set(response);
            final var rctx = new ResponseContext(
                    clock.instant(),
                    response.getStatusCode(),
                    response.getStatusText(),
                    response.getHeaders(),
                    BodySource.of(response),
                    false
            );
            return rctx;
        }

    }

}
