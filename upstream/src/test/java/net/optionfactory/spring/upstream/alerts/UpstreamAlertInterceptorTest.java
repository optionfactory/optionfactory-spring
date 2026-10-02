package net.optionfactory.spring.upstream.alerts;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverters;

public class UpstreamAlertInterceptorTest {

    @Upstream.AlertOnResponse(Upstream.AlertOnResponse.STATUS_IS_ERROR)
    @Upstream.AlertOnRemotingError
    public interface AlertingClient {

        String typeLevel();

        @Upstream.AlertOnResponse("#response.status().value() == 201")
        @Upstream.AlertOnRemotingError("#exception.message() == 'expected'")
        String methodLevel();
    }

    public interface QuietClient {

        String quiet();
    }

    private final List<Object> events = new ArrayList<>();
    private final Expressions expressions = new Expressions(null, null);

    private UpstreamAlertInterceptor interceptor(Class<?> k, ObservationRegistry observations) {
        final var endpoints = Stream.of(k.getMethods())
                .collect(Collectors.toMap(m -> m, m -> new EndpointDescriptor("up", m.getName(), m, null)));
        final var interceptor = new UpstreamAlertInterceptor(events::add, observations);
        interceptor.preprocess(k, expressions, endpoints);
        return interceptor;
    }

    private InvocationContext invocation(Class<?> k, String methodName) throws NoSuchMethodException {
        final var method = k.getMethod(methodName);
        final var converters = new MessageConverters(HttpMessageConverters.forClient().build());
        return new InvocationContext(expressions, PayloadsRendering.builder().build(), converters, new EndpointDescriptor("up", methodName, method, null), new Object[0], "boot", 1, null, Buffering.BUFFERED);
    }

    private static RequestContext request() {
        return new RequestContext(Instant.EPOCH, HttpMethod.GET, URI.create("http://example.com"), new HttpHeaders(), Map.of(), new byte[0]);
    }

    private static UpstreamHttpRequestExecution responding(HttpStatus status) {
        return (invocation, request) -> new ResponseContext(Instant.EPOCH, status, status.getReasonPhrase(), new HttpHeaders(), BodySource.of("body", StandardCharsets.UTF_8), false);
    }

    private static UpstreamHttpRequestExecution failing(String message) {
        return (invocation, request) -> {
            throw new IOException(message);
        };
    }

    @Test
    public void matchingResponseIsFlaggedAndPublished() throws Exception {
        final var got = interceptor(AlertingClient.class, ObservationRegistry.NOOP).intercept(invocation(AlertingClient.class, "typeLevel"), request(), responding(HttpStatus.BAD_GATEWAY));
        Assertions.assertTrue(got.alert(), "a response matching the condition must be flagged as alerted");
        Assertions.assertEquals(1, events.size(), "a response matching the condition must publish one alert");
        final var event = (UpstreamAlertEvent) events.get(0);
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY, event.response().status(), "the event must carry the response");
        Assertions.assertNull(event.exception(), "a response alert must not carry an exception");
        Assertions.assertEquals("body", new String(event.response().body().bytes(), StandardCharsets.UTF_8), "the event must carry the response body");
    }

    @Test
    public void nonMatchingResponseIsReturnedUntouched() throws Exception {
        final var got = interceptor(AlertingClient.class, ObservationRegistry.NOOP).intercept(invocation(AlertingClient.class, "typeLevel"), request(), responding(HttpStatus.OK));
        Assertions.assertFalse(got.alert(), "a response not matching the condition must not be flagged");
        Assertions.assertTrue(events.isEmpty(), "a response not matching the condition must not publish alerts");
    }

    @Test
    public void methodLevelConditionReplacesTheTypeLevelOne() throws Exception {
        final var interceptor = interceptor(AlertingClient.class, ObservationRegistry.NOOP);
        interceptor.intercept(invocation(AlertingClient.class, "methodLevel"), request(), responding(HttpStatus.BAD_GATEWAY));
        Assertions.assertTrue(events.isEmpty(), "the type level condition must not apply to a method declaring its own");
        interceptor.intercept(invocation(AlertingClient.class, "methodLevel"), request(), responding(HttpStatus.CREATED));
        Assertions.assertEquals(1, events.size(), "the method level condition must apply");
    }

    @Test
    public void remotingErrorIsPublishedAndRethrown() throws Exception {
        final var interceptor = interceptor(AlertingClient.class, ObservationRegistry.NOOP);
        final var invocation = invocation(AlertingClient.class, "typeLevel");
        final var request = request();
        final var thrown = Assertions.assertThrows(IOException.class, () -> interceptor.intercept(invocation, request, failing("boom")), "the remoting error must be rethrown");
        Assertions.assertEquals("boom", thrown.getMessage(), "the original exception must be rethrown");
        Assertions.assertEquals(1, events.size(), "a remoting error must publish one alert");
        final var event = (UpstreamAlertEvent) events.get(0);
        Assertions.assertEquals("boom", event.exception().message(), "the event must carry the exception message");
        Assertions.assertNull(event.response(), "a remoting alert has no response");
    }

    @Test
    public void remotingErrorNotMatchingTheConditionIsOnlyRethrown() throws Exception {
        final var interceptor = interceptor(AlertingClient.class, ObservationRegistry.NOOP);
        final var invocation = invocation(AlertingClient.class, "methodLevel");
        final var request = request();
        Assertions.assertThrows(IOException.class, () -> interceptor.intercept(invocation, request, failing("unexpected")), "the remoting error must be rethrown");
        Assertions.assertTrue(events.isEmpty(), "a remoting error not matching the condition must not publish alerts");
    }

    @Test
    public void unannotatedEndpointsNeverAlert() throws Exception {
        final var interceptor = interceptor(QuietClient.class, ObservationRegistry.NOOP);
        final var invocation = invocation(QuietClient.class, "quiet");
        final var request = request();
        Assertions.assertFalse(interceptor.intercept(invocation, request, responding(HttpStatus.BAD_GATEWAY)).alert(), "an error response must not be flagged without annotations");
        Assertions.assertThrows(IOException.class, () -> interceptor.intercept(invocation, request, failing("boom")), "the remoting error must be rethrown");
        Assertions.assertTrue(events.isEmpty(), "endpoints without alert annotations must never publish alerts");
    }

    @Test
    public void currentObservationIsTaggedWithTheAlertKind() throws Exception {
        final var observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(context -> true);
        final var interceptor = interceptor(AlertingClient.class, observations);
        final var invocation = invocation(AlertingClient.class, "typeLevel");
        final var request = request();
        final var response = Observation.start("test", observations);
        try (final var scope = response.openScope()) {
            interceptor.intercept(invocation, request, responding(HttpStatus.BAD_GATEWAY));
        }
        Assertions.assertEquals("response", response.getContext().getLowCardinalityKeyValue("alert").getValue(), "a response alert must tag the observation as such");
        final var remoting = Observation.start("test", observations);
        try (final var scope = remoting.openScope()) {
            Assertions.assertThrows(IOException.class, () -> interceptor.intercept(invocation, request, failing("boom")), "the remoting error must be rethrown");
        }
        Assertions.assertEquals("remoting", remoting.getContext().getLowCardinalityKeyValue("alert").getValue(), "a remoting alert must tag the observation as such");
    }
}
