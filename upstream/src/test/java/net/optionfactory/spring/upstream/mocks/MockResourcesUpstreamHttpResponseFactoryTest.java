package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

public class MockResourcesUpstreamHttpResponseFactoryTest {

    @Upstream.Mock.DefaultContentType("application/json")
    public interface Endpoints {

        @Upstream.Mock("missing.json")
        @Upstream.Mock("fallback.json")
        void fallback();

        @Upstream.Mock(value = "with-headers.json", status = HttpStatus.ACCEPTED, headers = "X-Annotated: #{#endpoint}")
        void withHeaders();

        @Upstream.Mock("missing-#{#id}.json")
        void missing(String id);

        @Upstream.Mock("bad-headers-file.json")
        void badHeadersFile();

        @Upstream.Mock("rendered.custom")
        void rendered();

        @Upstream.Mock(value = "fallback.json", headers = "Content-Type: text/plain")
        void annotatedContentType();

        @Upstream.Mock("typed.json")
        void resourceContentType();

        void unmocked();
    }

    private static final Expressions EXPRESSIONS = new Expressions(null, null);
    private static final Map<String, EndpointDescriptor> ENDPOINTS = Stream.of(Endpoints.class.getMethods())
            .collect(Collectors.toMap(m -> m.getName(), m -> new EndpointDescriptor("up", m.getName(), m, null)));

    private static MockResourcesUpstreamHttpResponseFactory factory(MocksRenderer... renderers) {
        final var factory = new MockResourcesUpstreamHttpResponseFactory(List.of(renderers));
        factory.preprocess(Endpoints.class, EXPRESSIONS, ENDPOINTS.values().stream().collect(Collectors.toMap(EndpointDescriptor::method, Function.identity())));
        return factory;
    }

    private static InvocationContext invocation(String endpoint, Object... args) {
        return new InvocationContext(EXPRESSIONS, null, null, ENDPOINTS.get(endpoint), args, "boot", 1, null, Buffering.BUFFERED);
    }

    private static ClientHttpResponse create(MockResourcesUpstreamHttpResponseFactory factory, InvocationContext invocation) {
        return factory.create(invocation, URI.create("http://example.com/"), HttpMethod.GET, new HttpHeaders());
    }

    private static String body(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static MocksRenderer renderer(boolean accepts, String output) {
        return new MocksRenderer() {
            @Override
            public boolean canRender(Resource source) {
                return accepts;
            }

            @Override
            public Resource render(Resource source, InvocationContext ctx) {
                return new ByteArrayResource(output.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Test
    public void theFirstMockWhoseResourceExistsAnswers() throws IOException {
        final var response = create(factory(), invocation("fallback"));
        Assertions.assertEquals("{\"from\":\"fallback\"}", body(response), "the missing first resource must be skipped in favour of the second");
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode(), "the status must default to 200");
        Assertions.assertEquals("OK", response.getStatusText(), "the status text must be the standard reason phrase");
    }

    @Test
    public void headersComeFromTheHeadersResourceAndFromTheAnnotation() throws IOException {
        final var response = create(factory(), invocation("withHeaders"));
        Assertions.assertEquals("file", response.getHeaders().getFirst("X-From-File"), "the .headers resource must contribute its headers");
        Assertions.assertEquals(List.of("one", "two"), response.getHeaders().get("X-Multi"), "a header repeated in the .headers resource must keep every value");
        Assertions.assertEquals("withHeaders", response.getHeaders().getFirst("X-Annotated"), "annotation headers must be evaluated as templates against the invocation");
        Assertions.assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(), "the annotation status must be used");
        Assertions.assertEquals("Accepted", response.getStatusText(), "the status text must be the reason phrase of the annotation status");
    }

    @Test
    public void aMissingResourceFailsNamingTheEndpoint() {
        final var factory = factory();
        final var ex = Assertions.assertThrows(RestClientException.class, () -> create(factory, invocation("missing", "42")), "no existing resource must fail the exchange");
        Assertions.assertTrue(ex.getMessage().contains("up:missing"), "the failure must name the upstream and the endpoint");
    }

    @Test
    public void aHeadersResourceLineWithoutColonFails() {
        final var factory = factory();
        Assertions.assertThrows(RestClientException.class, () -> create(factory, invocation("badHeadersFile")), "a .headers line without ':' must fail the exchange");
    }

    @Test
    public void theFirstRendererAcceptingTheResourceRendersIt() throws IOException {
        final var response = create(factory(renderer(false, "rejecting"), renderer(true, "first"), renderer(true, "second")), invocation("rendered"));
        Assertions.assertEquals("first", body(response), "renderers must be tried in order, the first accepting one winning");
    }

    @Test
    public void aResourceNoRendererAcceptsIsServedAsItIs() throws IOException {
        final var response = create(factory(renderer(false, "rejecting")), invocation("rendered"));
        Assertions.assertEquals("raw", body(response), "without an accepting renderer the resource must be served raw");
    }

    @Test
    public void headersFromResourceAreEmptyWithoutAHeadersResource() {
        final var headers = MockResourcesUpstreamHttpResponseFactory.headersFromResource("fallback.json", invocation("fallback"));
        Assertions.assertTrue(headers.isEmpty(), "a mock without a .headers resource must have no resource headers");
    }

    @Test
    public void theDefaultContentTypeIsUsedWhenNoOtherIsGiven() {
        final var response = create(factory(), invocation("fallback"));
        Assertions.assertEquals(List.of("application/json"), response.getHeaders().get(HttpHeaders.CONTENT_TYPE), "the DefaultContentType must be used when neither the .headers resource nor the annotation give one");
    }

    @Test
    public void anAnnotationContentTypeOverridesTheDefault() {
        final var response = create(factory(), invocation("annotatedContentType"));
        Assertions.assertEquals(List.of("text/plain"), response.getHeaders().get(HttpHeaders.CONTENT_TYPE), "the annotation Content-Type must replace the default one");
        Assertions.assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType(), "the annotation Content-Type must be the response content type");
    }

    @Test
    public void aHeadersResourceContentTypeOverridesTheDefault() {
        final var response = create(factory(), invocation("resourceContentType"));
        Assertions.assertEquals(List.of("application/xml"), response.getHeaders().get(HttpHeaders.CONTENT_TYPE), "the .headers resource Content-Type must replace the default one");
    }

    @Test
    public void anEndpointWithoutMockFailsNamingTheEndpoint() {
        final var factory = factory();
        final var ex = Assertions.assertThrows(RestClientException.class, () -> create(factory, invocation("unmocked")), "an endpoint without @Upstream.Mock must fail the exchange with a clear error");
        Assertions.assertTrue(ex.getMessage().contains("up:unmocked"), "the failure must name the upstream and the endpoint: " + ex.getMessage());
    }
}
