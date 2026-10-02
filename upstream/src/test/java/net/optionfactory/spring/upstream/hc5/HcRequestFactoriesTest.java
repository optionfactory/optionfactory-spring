package net.optionfactory.spring.upstream.hc5;

import java.time.format.DateTimeParseException;
import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.expressions.Expressions;
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

    @Upstream.HttpComponents(maxConnections = "#{#max}", maxConnectionsType = Expressions.Type.TEMPLATED, maxConnectionsPerRoute = "#{#max}", maxConnectionsPerRouteType = Expressions.Type.TEMPLATED)
    public interface TemplatedPoolClient {

        @GetExchange("/")
        Map<String, String> get();
    }

    @Upstream.HttpComponents(maxConnections = "#max", maxConnectionsType = Expressions.Type.STATIC)
    public interface StaticPoolClient {

        @GetExchange("/")
        Map<String, String> get();
    }

    @Upstream.HttpComponents(maxConnections = "#max * 2", maxConnectionsPerRoute = "#max")
    public interface DefaultTypePoolClient {

        @GetExchange("/")
        Map<String, String> get();
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
    public void templatedPoolSizesAreRenderedWithTheBuilderVariables() {
        Assertions.assertDoesNotThrow(() -> UpstreamBuilder.create(TemplatedPoolClient.class)
                .var("max", 5)
                .requestFactoryHttpComponents(c -> {
                })
                .build(), "TEMPLATED pool sizes must be rendered as templates with the builder variables");
    }

    @Test
    public void staticPoolSizesAreNotEvaluated() {
        final var builder = UpstreamBuilder.create(StaticPoolClient.class)
                .var("max", 5)
                .requestFactoryHttpComponents(c -> {
                });
        Assertions.assertThrows(NumberFormatException.class, builder::build, "a STATIC pool size must be parsed as an int, not evaluated as an expression");
    }

    @Test
    public void poolSizesAreExpressionsByDefault() {
        Assertions.assertDoesNotThrow(() -> UpstreamBuilder.create(DefaultTypePoolClient.class)
                .var("max", 5)
                .requestFactoryHttpComponents(c -> {
                })
                .build(), "pool sizes without an explicit type must be evaluated as SpEL expressions");
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
