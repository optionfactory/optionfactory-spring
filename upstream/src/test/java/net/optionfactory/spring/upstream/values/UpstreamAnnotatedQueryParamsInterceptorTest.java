package net.optionfactory.spring.upstream.values;

import java.net.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UpstreamAnnotatedQueryParamsInterceptorTest {

    @Test
    public void paramsAreAppendedInDeclarationOrderAfterTheExistingOnes() throws Exception {
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedQueryParamsInterceptor(), AnnotatedValues.invocation("annotated", "alice"), AnnotatedValues.request("http://example.com/path?existing=1"));
        Assertions.assertEquals(URI.create("http://example.com/path?existing=1&q=alice&page=2"), executed.uri(), "params whose condition holds must be appended after the existing ones, in declaration order");
    }

    @Test
    public void theRequestIsPassedOnUnchangedWhenNoParamIsAppended() throws Exception {
        final var request = AnnotatedValues.request("http://example.com/path?existing=1");
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedQueryParamsInterceptor(), AnnotatedValues.invocation("conditionsNeverHold", "alice"), request);
        Assertions.assertSame(request, executed, "when no condition holds the very same request must be passed on");
    }
}
