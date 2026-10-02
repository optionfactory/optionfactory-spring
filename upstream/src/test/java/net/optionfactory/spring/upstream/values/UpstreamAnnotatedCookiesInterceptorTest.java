package net.optionfactory.spring.upstream.values;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UpstreamAnnotatedCookiesInterceptorTest {

    @Test
    public void eachCookieWhoseConditionHoldsIsAFurtherCookieHeader() throws Exception {
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedCookiesInterceptor(), AnnotatedValues.invocation("annotated", "alice"), AnnotatedValues.request("http://example.com/"));
        Assertions.assertEquals(List.of("first=alice", "second=2"), executed.headers().get("Cookie"), "every cookie whose condition holds must be added, in declaration order, as a template");
    }

    @Test
    public void endpointsWithoutAnnotationsGetNoCookie() throws Exception {
        final var executed = AnnotatedValues.intercept(new UpstreamAnnotatedCookiesInterceptor(), AnnotatedValues.invocation("bare", "alice"), AnnotatedValues.request("http://example.com/"));
        Assertions.assertFalse(executed.headers().containsHeader("Cookie"), "an endpoint without annotations must get no cookie");
    }
}
