package net.optionfactory.spring.upstream.micometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamInterceptor;

public class UpstreamMicrometerInterceptor<CTX> implements UpstreamInterceptor<CTX> {
    
    private final MeterRegistry metrics;

    public UpstreamMicrometerInterceptor(MeterRegistry metrics) {
        this.metrics = metrics;
    }
    @Override
    public void success(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        Timer.builder("upstream_duration_seconds")
              .tags("upstream", prepare.upstreamId)
              .tags("endpoint", prepare.endpointId)
              .tags("response.status", response.status.name())
              .tags("outcome", "success")
              .register(metrics)
              .record(Duration.between(request.at, response.at));
    }

    @Override
    public void error(PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
        Timer.builder("upstream_duration_seconds")
              .tags("upstream", prepare.upstreamId)
              .tags("endpoint", prepare.endpointId)
              .tags("response.status", "NO_RESPONSE")
              .tags("outcome", "error")
              .register(metrics)
              .record(Duration.between(request.at, error.at));
    }

}
