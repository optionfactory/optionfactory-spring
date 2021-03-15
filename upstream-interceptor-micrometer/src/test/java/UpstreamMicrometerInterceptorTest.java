
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.optionfactory.spring.upstream.UpstreamInterceptor;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ExchangeContext;
import net.optionfactory.spring.upstream.micometer.UpstreamMicrometerInterceptor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;

public class UpstreamMicrometerInterceptorTest {

    @Test
    public void a() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final UpstreamMicrometerInterceptor<String> interceptor = new UpstreamMicrometerInterceptor<>(registry);

        ExchangeContext<String> ctx = new UpstreamInterceptor.ExchangeContext<>();
        ctx.prepare = new UpstreamInterceptor.PrepareContext<>();
        ctx.prepare.requestId = "123";
        ctx.prepare.endpointId = "endpoint";
        ctx.prepare.upstreamId = "vita";
        ctx.request = new UpstreamInterceptor.RequestContext();
        ctx.request.at = Instant.now().minus(10, ChronoUnit.SECONDS);
        ctx.response = new UpstreamInterceptor.ResponseContext();
        ctx.response.at = Instant.now();
        ctx.response.status = HttpStatus.I_AM_A_TEAPOT;

        interceptor.remotingSuccess(ctx.prepare, ctx.request, ctx.response);

        Assert.assertEquals("upstream_duration_seconds", registry.getMeters().get(0).getId().getName());
    }

}
