package net.optionfactory.spring.upstream.observations;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.util.Objects;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public class UpstreamObservingInterceptor implements UpstreamHttpInterceptor {

    private final ObservationRegistry observations;

    public UpstreamObservingInterceptor(ObservationRegistry observations) {
        this.observations = observations;
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        return Observation.createNotStarted("upstream", observations)
                .lowCardinalityKeyValue("upstream", invocation.endpoint().upstream())
                .lowCardinalityKeyValue("endpoint", invocation.endpoint().name())
                .lowCardinalityKeyValue("fault", "none")
                .highCardinalityKeyValue("boot", invocation.boot())
                .highCardinalityKeyValue("rid", Long.toString(request.id()))
                .highCardinalityKeyValue("principal", Objects.toString(invocation.principal()))
                .highCardinalityKeyValue("http.method", Objects.toString(request.method()))
                .highCardinalityKeyValue("http.uri", Objects.toString(request.uri()))
                .observeChecked(() -> execution.execute(invocation, request));
    }

}
