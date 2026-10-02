package net.optionfactory.spring.upstream.soap;

import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;

public class UpstreamSoapActionInitializerTest {

    public interface Client {

        @Upstream.SoapAction("urn:#{#operation}")
        void templated(String operation);

        void withoutAction();
    }

    private static final Expressions EXPRESSIONS = new Expressions(null, null);

    private static MockClientHttpRequest initialize(Protocol protocol, String method, Object... args) throws NoSuchMethodException {
        final var templated = Client.class.getMethod("templated", String.class);
        final var withoutAction = Client.class.getMethod("withoutAction");
        final var endpoints = Map.of(
                templated, new EndpointDescriptor("up", "templated", templated, null),
                withoutAction, new EndpointDescriptor("up", "withoutAction", withoutAction, null)
        );
        final var initializer = new UpstreamSoapActionInitializer(protocol);
        initializer.preprocess(Client.class, EXPRESSIONS, endpoints);
        final var endpoint = endpoints.get(method.equals("templated") ? templated : withoutAction);
        final var invocation = new InvocationContext(EXPRESSIONS, null, null, endpoint, args, "boot", 1, null, Buffering.BUFFERED);
        final var request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://example.com/"));
        initializer.initialize(invocation, request);
        return request;
    }

    @Test
    public void theActionIsATemplateEvaluatedAgainstTheInvocation() throws NoSuchMethodException {
        final var request = initialize(Protocol.SOAP_1_1, "templated", "Add");
        Assertions.assertEquals("\"urn:Add\"", request.getHeaders().getFirst("SOAPAction"), "the action template must be evaluated with the method parameters");
    }

    @Test
    public void anEndpointWithoutActionGetsTheContentTypeOnly() throws NoSuchMethodException {
        final var request = initialize(Protocol.SOAP_1_1, "withoutAction");
        Assertions.assertEquals(MediaType.TEXT_XML, request.getHeaders().getContentType(), "the protocol Content-Type must be set anyway");
        Assertions.assertFalse(request.getHeaders().containsHeader("SOAPAction"), "an endpoint without @SoapAction must send no action");
    }
}
