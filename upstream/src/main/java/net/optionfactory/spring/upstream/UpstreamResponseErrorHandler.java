package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.util.List;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ExchangeContext;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

public class UpstreamResponseErrorHandler<CTX> extends DefaultResponseErrorHandler {

    private final String upstreamId;
    private final List<UpstreamInterceptor<CTX>> interceptors;
    private final ThreadLocal<ExchangeContext<CTX>> callContexts;

    public UpstreamResponseErrorHandler(String upstreamId, List<UpstreamInterceptor<CTX>> interceptors, ThreadLocal<ExchangeContext<CTX>> callContexts) {
        this.upstreamId = upstreamId;
        this.interceptors = interceptors;
        this.callContexts = callContexts;
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        final var rawStatusCode = response.getRawStatusCode();
        final var ctx = callContexts.get();
        interceptors.stream()
                .map(i -> i.errorStrategy(ctx.prepare, ctx.request, ctx.response))
                .filter(r -> r.isPresent())
                .map(r -> r.get())
                .findFirst()
                .ifPresentOrElse(result -> {
                    if (result.success) {
                        return;
                    }
                    throw new UpstreamException(upstreamId, result.failureReason, result.failureDetails);
                }, () -> {
                    throw new UpstreamException(upstreamId, "GENERIC_ERROR", Integer.toString(rawStatusCode));
                });
    }
}
