package net.optionfactory.spring.upstream.micometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

public class UpstreamMicrometerInterceptor implements UpstreamHttpInterceptor {

    private final MeterRegistry metrics;

    public UpstreamMicrometerInterceptor(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            final var response = execution.execute(request, body);
            Timer.builder("upstream_duration_seconds")
                    .tags("upstream", ctx.upstream())
                    .tags("endpoint", ctx.endpoint())
                    .tags("response.status", response.getStatusCode().toString())
                    .tags("outcome", "success")
                    .register(metrics)
                    .record(Duration.between(ctx.requestedAt(), ctx.clock().instant()));
            return response;
        } catch (Exception ex) {
            Timer.builder("upstream_duration_seconds")
                    .tags("upstream", ctx.upstream())
                    .tags("endpoint", ctx.endpoint())
                    .tags("response.status", "NO_RESPONSE")
                    .tags("outcome", "error")
                    .register(metrics)
                    .record(Duration.between(ctx.requestedAt(), ctx.clock().instant()));
            throw ex;
        }
    }

}
