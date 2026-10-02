package net.optionfactory.spring.upstream.values;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UpstreamAnnotatedHeadersInterceptorTest {

    @Test
    public void headersWhoseConditionHoldsAreAdded() throws Exception {
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedHeadersInterceptor(), AnnotatedValues.invocation("annotated", "alice"), AnnotatedValues.request("http://example.com/"));
        Assertions.assertEquals("alice", executed.headers().getFirst("X-annotated"), "the key template and the value expression must be evaluated against the invocation");
        Assertions.assertEquals(List.of("existing", "kept"), executed.headers().get("X-Static"), "values must be added after the existing ones, skipping annotations whose condition does not hold");
    }

    @Test
    public void endpointsWithoutAnnotationsAreLeftAlone() throws Exception {
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedHeadersInterceptor(), AnnotatedValues.invocation("bare", "alice"), AnnotatedValues.request("http://example.com/"));
        Assertions.assertEquals(List.of("existing"), executed.headers().get("X-Static"), "an endpoint without annotations must keep its headers");
        Assertions.assertEquals(1, executed.headers().size(), "an endpoint without annotations must get no header");
    }
}
