package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

public class UpstreamBuilderTest {

    public interface QueryVarClient {

        @GetExchange("/")
        @Upstream.QueryParam(key = "v", value = "#v")
        Map<String, String> get();
    }

    @Upstream("annotated-name")
    public interface NamedClient {

        @GetExchange("/named/{id}")
        @Upstream.Endpoint("named-endpoint")
        @Upstream.Header(key = "X-Token", value = "#token")
        Map<String, String> call(@PathVariable String id, @Upstream.Principal String principal, @Upstream.Context String context);

        @GetExchange("/unnamed")
        Map<String, String> unnamed();
    }

    @Upstream
    public interface BlankNameClient {

        @GetExchange("/")
        Map<String, String> call();
    }

    private record Seen(InvocationContext invocation, URI uri, HttpHeaders headers) {

    }

    private static <T> UpstreamBuilder<T> recording(Class<T> k, List<Seen> seen, HttpStatus status) {
        return UpstreamBuilder.create(k)
                .requestFactoryMock(c -> c.responseFactory((invocation, uri, method, headers) -> {
                    seen.add(new Seen(invocation, uri, HttpHeaders.copyOf(headers)));
                    final var h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    return new MockClientHttpResponse(status, status.getReasonPhrase(), h, new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)));
                }))
                .json(new JsonMapper())
                .var("token", "secret")
                .baseUri("http://example.com");
    }

    @Test
    public void typeIsRequired() {
        final var builder = UpstreamBuilder.create().requestFactory(new MockClientHttpRequestFactory());
        Assertions.assertThrows(IllegalArgumentException.class, builder::build, "building without an interface must fail");
    }

    @Test
    public void requestFactoryIsRequired() {
        final var builder = UpstreamBuilder.create(NamedClient.class);
        Assertions.assertThrows(IllegalArgumentException.class, builder::build, "building without a request factory must fail");
    }

    @Test
    public void requestFactoryCanBeConfiguredOnlyOnce() {
        final var builder = UpstreamBuilder.create(NamedClient.class).requestFactoryMock(c -> {
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.requestFactoryHttpComponents(c -> {
        }), "a second request factory must be rejected");
    }

    @Test
    public void conditionalRequestFactoriesAreSkippedWhenTheConditionIsFalse() {
        final var client = UpstreamBuilder.create(BlankNameClient.class)
                .requestFactoryHttpComponentsIf(false, c -> {
                })
                .requestFactoryMockIf(true, c -> c.response(MediaType.APPLICATION_JSON, "{\"k\":\"v\"}"))
                .json(new JsonMapper())
                .baseUri("http://example.com")
                .build();
        Assertions.assertEquals(Map.of("k", "v"), client.call(), "a skipped factory must leave room for the one whose condition holds");
    }

    @Test
    public void upstreamAndEndpointNamesComeFromTheAnnotations() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).build().call("1", null, null);
        Assertions.assertEquals("annotated-name", seen.get(0).invocation().endpoint().upstream(), "the @Upstream value must name the upstream");
        Assertions.assertEquals("named-endpoint", seen.get(0).invocation().endpoint().name(), "the @Upstream.Endpoint value must name the endpoint");
    }

    @Test
    public void explicitNameWinsOverTheAnnotation() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).name("explicit").build().unnamed();
        Assertions.assertEquals("explicit", seen.get(0).invocation().endpoint().upstream(), "the builder name must win over @Upstream");
        Assertions.assertEquals("unnamed", seen.get(0).invocation().endpoint().name(), "an endpoint without @Upstream.Endpoint must be named after its method");
    }

    @Test
    public void blankNameFallsBackToTheInterfaceName() {
        final var seen = new ArrayList<Seen>();
        recording(BlankNameClient.class, seen, HttpStatus.OK).build().call();
        Assertions.assertEquals("BlankNameClient", seen.get(0).invocation().endpoint().upstream(), "a blank @Upstream value must fall back to the interface simple name");
    }

    @Test
    public void principalParameterWinsOverTheSupplier() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).principal(() -> "supplied").build().call("1", "argument", null);
        Assertions.assertEquals("argument", seen.get(0).invocation().principal(), "the @Upstream.Principal argument must be the principal");
    }

    @Test
    public void nullPrincipalParameterFallsBackToTheSupplier() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).principal(() -> "supplied").build().call("1", null, null);
        Assertions.assertEquals("supplied", seen.get(0).invocation().principal(), "a null principal argument must fall back to the supplier");
    }

    @Test
    public void principalAndContextParametersAreNotSent() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).build().call("1", "principal", "context");
        Assertions.assertEquals(URI.create("http://example.com/named/1"), seen.get(0).uri(), "principal and context arguments must not reach the request");
        Assertions.assertArrayEquals(new Object[]{"1", "principal", "context"}, seen.get(0).invocation().arguments(), "every argument must still be available to the invocation");
    }

    @Test
    public void variablesAreAvailableToAnnotationExpressions() {
        final var seen = new ArrayList<Seen>();
        recording(NamedClient.class, seen, HttpStatus.OK).build().call("1", null, null);
        Assertions.assertEquals("secret", seen.get(0).headers().getFirst("X-Token"), "a builder variable must be usable in annotation expressions");
    }

    @Test
    public void copiesFromThePrototypeAreIndependent() {
        final var seen = new ArrayList<Seen>();
        final var prototype = recording(BlankNameClient.class, seen, HttpStatus.OK);
        prototype.builder().initializer((invocation, request) -> request.getHeaders().set("X-Copy", "yes")).build().call();
        prototype.build().call();
        Assertions.assertEquals("yes", seen.get(0).headers().getFirst("X-Copy"), "the copy must apply its own initializer");
        Assertions.assertNull(seen.get(1).headers().getFirst("X-Copy"), "customizing a copy must not alter the prototype");
    }

    @Test
    public void customInterceptorsRunBeforeTheBuiltInOnes() {
        final var seen = new ArrayList<Seen>();
        final var headersSeenByInterceptor = new ArrayList<HttpHeaders>();
        recording(NamedClient.class, seen, HttpStatus.OK)
                .interceptor(new UpstreamHttpInterceptor() {
                    @Override
                    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
                        headersSeenByInterceptor.add(HttpHeaders.copyOf(request.headers()));
                        return execution.execute(invocation, request);
                    }
                })
                .build()
                .call("1", null, null);
        Assertions.assertNull(headersSeenByInterceptor.get(0).getFirst("X-Token"), "a custom interceptor must see the request before the annotated headers are added");
        Assertions.assertEquals("secret", seen.get(0).headers().getFirst("X-Token"), "the annotated headers must still be sent");
    }

    @Test
    public void customResponseErrorHandlersRunBeforeTheBuiltInOnes() {
        final var client = recording(BlankNameClient.class, new ArrayList<>(), HttpStatus.NOT_FOUND)
                .responseErrorHandler(new UpstreamResponseErrorHandler() {
                    @Override
                    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) {
                        return response.status().value() == 404;
                    }

                    @Override
                    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) {
                        throw new IllegalStateException("custom");
                    }
                })
                .build();
        final var ex = Assertions.assertThrows(IllegalStateException.class, client::call, "the custom handler must handle the error before the built-in status handler");
        Assertions.assertEquals("custom", ex.getMessage(), "the custom handler exception must reach the caller");
    }

    @Test
    public void customResponseErrorHandlerCanSwallowAnErrorStatus() {
        final var client = recording(BlankNameClient.class, new ArrayList<>(), HttpStatus.NOT_FOUND)
                .responseErrorHandler(new UpstreamResponseErrorHandler() {
                    @Override
                    public boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) {
                        return true;
                    }

                    @Override
                    public void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) {
                    }
                })
                .build();
        Assertions.assertEquals(Map.of(), client.call(), "a handler that claims the response and does not throw must stop the built-in handlers");
    }

    @Test
    public void errorStatusWithoutCustomHandlersFails() {
        final var client = recording(BlankNameClient.class, new ArrayList<>(), HttpStatus.NOT_FOUND).build();
        Assertions.assertThrows(RestClientUpstreamException.class, client::call, "an error status must fail the call by default");
    }

    private static class MockClientHttpRequestFactory implements org.springframework.http.client.ClientHttpRequestFactory {

        @Override
        public org.springframework.http.client.ClientHttpRequest createRequest(URI uri, org.springframework.http.HttpMethod httpMethod) {
            return new MockClientHttpRequest(httpMethod, uri);
        }
    }

    @Test
    public void variablesSetAfterBuildDoNotChangeBuiltClients() {
        final var seen = new ArrayList<Seen>();
        final var builder = recording(QueryVarClient.class, seen, HttpStatus.OK).var("v", "before");
        final var client = builder.build();
        builder.var("v", "after");
        client.get();
        Assertions.assertEquals(URI.create("http://example.com/?v=before"), seen.get(0).uri(), "a variable changed on the builder after build() must not change the clients already built");
    }
}
