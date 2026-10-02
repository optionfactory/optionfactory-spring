package net.optionfactory.spring.upstream.hc5;

import java.time.format.DateTimeParseException;
import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.buffering.Buffering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.service.annotation.GetExchange;

public class HcRequestFactoriesTest {

    @Upstream.HttpComponents(connectionTimeout = "#{#connectionTimeout}", socketTimeout = "PT#{#socketSeconds}S")
    public interface TemplatedClient {

        @GetExchange("/")
        Map<String, String> get();
    }

    public interface InheritingClient extends TemplatedClient {

    }

    @Test
    public void standaloneFactoryIsBufferedOnRequest() {
        Assertions.assertInstanceOf(BufferingClientHttpRequestFactory.class, HcRequestFactories.builder().build(Buffering.BUFFERED), "a BUFFERED factory must buffer request bodies");
    }

    @Test
    public void standaloneFactoryIsUnbufferedOtherwise() {
        Assertions.assertInstanceOf(HttpComponentsClientHttpRequestFactory.class, HcRequestFactories.builder().build(Buffering.UNBUFFERED), "an UNBUFFERED factory must be the plain http components one");
        Assertions.assertInstanceOf(HttpComponentsClientHttpRequestFactory.class, HcRequestFactories.builder().build(Buffering.UNBUFFERED_STREAMING), "an UNBUFFERED_STREAMING factory must be the plain http components one");
    }

    @Test
    public void templatedTimeoutsAreResolvedAgainstTheBuilderVariables() {
        Assertions.assertDoesNotThrow(() -> UpstreamBuilder.create(TemplatedClient.class)
                .var("connectionTimeout", "PT1S")
                .var("socketSeconds", 2)
                .requestFactoryHttpComponents(c -> {
                })
                .build(), "templated timeouts must be rendered with the builder variables before being parsed");
    }

    @Test
    public void annotationIsInheritedFromSuperInterfaces() {
        final var builder = UpstreamBuilder.create(InheritingClient.class)
                .var("connectionTimeout", "not a duration")
                .var("socketSeconds", 2)
                .requestFactoryHttpComponents(c -> {
                });
        Assertions.assertThrows(DateTimeParseException.class, builder::build, "the super-interface configuration must be applied, failing on its unparseable timeout");
    }

    @Test
    public void unparseableTimeoutFailsTheBuild() {
        final var builder = UpstreamBuilder.create(TemplatedClient.class)
                .var("connectionTimeout", "five seconds")
                .var("socketSeconds", 2)
                .requestFactoryHttpComponents(c -> {
                });
        Assertions.assertThrows(DateTimeParseException.class, builder::build, "a timeout that is not an ISO-8601 duration must fail when the client is built");
    }
}
