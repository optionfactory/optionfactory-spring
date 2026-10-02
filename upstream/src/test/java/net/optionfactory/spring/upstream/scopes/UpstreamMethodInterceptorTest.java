package net.optionfactory.spring.upstream.scopes;

import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.RestClientException;

public class UpstreamMethodInterceptorTest {

    public interface Client {

        String withPrincipal(@Upstream.Principal String principal);

        String plain();

        default String defaulted() {
            return "default";
        }
    }

    private final ThreadLocal<InvocationContext> invocations = new ThreadLocal<>();
    private final ThreadLocal<RequestContext> requests = new ThreadLocal<>();
    private final ThreadLocal<ResponseContext> responses = new ThreadLocal<>();
    private final List<Object> events = new ArrayList<>();
    private final List<InvocationContext> seen = new ArrayList<>();

    private Client proxy(Client target) throws NoSuchMethodException {
        final Method withPrincipal = Client.class.getMethod("withPrincipal", String.class);
        final Method plain = Client.class.getMethod("plain");
        final Map<Method, EndpointDescriptor> endpoints = Map.of(
                withPrincipal, new EndpointDescriptor("up", "withPrincipal", withPrincipal, 0),
                plain, new EndpointDescriptor("up", "plain", plain, null)
        );
        final Supplier<Object> principal = () -> "from-supplier";
        final var interceptor = new UpstreamMethodInterceptor(endpoints, invocations, principal, null, null, null, ObservationRegistry.NOOP, requests, responses, InstantSource.fixed(Instant.EPOCH), events::add);
        final var factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(Client.class);
        factory.addAdvice(interceptor);
        return (Client) factory.getProxy();
    }

    private Client recording() throws NoSuchMethodException {
        return proxy(new Client() {
            @Override
            public String withPrincipal(String principal) {
                seen.add(invocations.get());
                return "called";
            }

            @Override
            public String plain() {
                seen.add(invocations.get());
                requests.set(new RequestContext(Instant.EPOCH, null, null, new HttpHeaders(), Map.of(), new byte[0]));
                return "called";
            }
        });
    }

    @Test
    public void theInvocationIsInScopeDuringTheCallOnly() throws NoSuchMethodException {
        final var client = recording();
        Assertions.assertEquals("called", client.plain(), "the call must reach the target");
        Assertions.assertEquals("plain", seen.get(0).endpoint().name(), "the invocation of the called endpoint must be in scope during the call");
        Assertions.assertNull(invocations.get(), "the invocation must be removed when the call ends");
        Assertions.assertNull(requests.get(), "the request recorded during the call must be removed when the call ends");
    }

    @Test
    public void eachInvocationGetsANewIncreasingId() throws NoSuchMethodException {
        final var client = recording();
        client.plain();
        client.plain();
        Assertions.assertTrue(seen.get(1).id() > seen.get(0).id(), "each invocation must get a new, increasing id");
    }

    @Test
    public void aPrincipalArgumentWinsOverTheSupplier() throws NoSuchMethodException {
        final var client = recording();
        client.withPrincipal("from-argument");
        client.withPrincipal(null);
        client.plain();
        Assertions.assertEquals("from-argument", seen.get(0).principal(), "the principal argument must be the invocation principal");
        Assertions.assertEquals("from-supplier", seen.get(1).principal(), "a null principal argument must fall back to the supplier");
        Assertions.assertEquals("from-supplier", seen.get(2).principal(), "an endpoint without a principal parameter must use the supplier");
    }

    @Test
    public void theScopeIsRemovedWhenTheCallFails() throws NoSuchMethodException {
        final var client = proxy(new Client() {
            @Override
            public String withPrincipal(String principal) {
                throw new IllegalStateException("boom");
            }

            @Override
            public String plain() {
                return "called";
            }
        });
        Assertions.assertThrows(IllegalStateException.class, () -> client.withPrincipal("p"), "the failure of the call must be rethrown");
        Assertions.assertNull(invocations.get(), "the invocation must be removed when the call fails");
    }

    @Test
    public void defaultMethodsRunOutsideAnyScope() throws NoSuchMethodException {
        Assertions.assertEquals("default", recording().defaulted(), "a default method must be invoked directly");
        Assertions.assertTrue(seen.isEmpty(), "a default method must not reach the target");
    }

    private Client failingWith(RestClientException ex, boolean alreadyAlerted) throws NoSuchMethodException {
        return proxy(new Client() {
            @Override
            public String withPrincipal(String principal) {
                throw ex;
            }

            @Override
            public String plain() {
                responses.set(new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", new HttpHeaders(), ResponseContext.BodySource.of(new byte[0]), alreadyAlerted));
                throw ex;
            }
        });
    }

    private static RestClientException mappingFailure() {
        return new RestClientException("cannot map", new HttpMessageNotReadableException("unexpected token", new MockHttpInputMessage(new byte[0])));
    }

    @Test
    public void aMappingFailurePublishesAnAlert() throws NoSuchMethodException {
        final var client = failingWith(mappingFailure(), false);
        Assertions.assertThrows(RestClientException.class, client::plain, "the mapping failure must be rethrown");
        Assertions.assertEquals(1, events.size(), "a mapping failure must publish an alert");
        final var event = Assertions.assertInstanceOf(UpstreamAlertEvent.class, events.get(0), "the published event must be an alert");
        Assertions.assertEquals("unexpected token", event.exception().message(), "the alert must carry the message of the mapping failure cause");
        Assertions.assertEquals("plain", event.invocation().endpoint().name(), "the alert must carry the invocation");
    }

    @Test
    public void aMappingFailureOfAnAlreadyAlertedResponseIsNotAlertedAgain() throws NoSuchMethodException {
        final var client = failingWith(mappingFailure(), true);
        Assertions.assertThrows(RestClientException.class, client::plain, "the mapping failure must be rethrown");
        Assertions.assertTrue(events.isEmpty(), "a response already alerted must not be alerted again");
    }

    @Test
    public void otherClientFailuresAreNotAlerted() throws NoSuchMethodException {
        final var client = failingWith(new RestClientException("connection refused"), false);
        Assertions.assertThrows(RestClientException.class, client::plain, "the failure must be rethrown");
        Assertions.assertTrue(events.isEmpty(), "a failure that is not a mapping one must not publish an alert");
    }
}
