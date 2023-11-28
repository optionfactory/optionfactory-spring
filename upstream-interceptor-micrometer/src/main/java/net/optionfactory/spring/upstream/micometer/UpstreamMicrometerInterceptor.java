package net.optionfactory.spring.upstream.micometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public class UpstreamMicrometerInterceptor implements UpstreamHttpInterceptor {

    private final MeterRegistry metrics;

    public UpstreamMicrometerInterceptor(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    public ResponseContext intercept(InvocationContext ctx, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        try {
            final var response = execution.execute(ctx, request);
            Timer.builder("upstream_duration_seconds")
                    .tags("upstream", ctx.upstream())
                    .tags("endpoint", ctx.endpoint())
                    .tags("response.status", response.status().toString())
                    .tags("outcome", "success")
                    .register(metrics)
                    .record(Duration.between(request.at(), response.at()));
            return response;
        } catch (Exception ex) {
            Timer.builder("upstream_duration_seconds")
                    .tags("upstream", ctx.upstream())
                    .tags("endpoint", ctx.endpoint())
                    .tags("response.status", "NO_RESPONSE")
                    .tags("outcome", "error")
                    .register(metrics)
                    .record(Duration.between(request.at(), Instant.now()));
            throw ex;
        }
    }

}
