package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

public class UpstreamLoggingInterceptorTest {

    @Upstream.Logging(requestMaxSize = 1, responseMaxSize = 1)
    public interface LoggedClient {

        String typeLevel();

        @Upstream.Logging(requestMaxSize = 2, responseMaxSize = 2)
        String methodLevel();
    }

    public interface UnloggedClient {

        String unlogged();
    }

    private static class RecordingRendering extends PayloadsRendering {

        public final List<Integer> requestSizes = new ArrayList<>();
        public final List<Integer> responseSizes = new ArrayList<>();

        public RecordingRendering() {
            super(null, null, null, null, null);
        }

        @Override
        public RenderedRequest render(RequestContext request, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
            requestSizes.add(maxSize);
            return new RenderedRequest(request.uri(), new RenderedPart(new HttpHeaders(), ""), List.of());
        }

        @Override
        public RenderedResponse render(ResponseContext response, MultipartStrategy mps, HeadersStrategy hs, BodiesStrategy bs, String infix, int maxSize) {
            responseSizes.add(maxSize);
            return new RenderedResponse(new RenderedPart(new HttpHeaders(), ""), List.of());
        }
    }

    private final RecordingRendering rendering = new RecordingRendering();
    private final Expressions expressions = new Expressions(null, null);

    private static Upstream.Logging.Conf sized(int size) {
        final var d = Upstream.Logging.Conf.defaults();
        return new Upstream.Logging.Conf(d.requestMultipart(), d.requestHeaders(), d.requestBody(), size, d.responseMultipart(), d.responseHeaders(), d.responseBody(), size, d.infix());
    }

    private UpstreamLoggingInterceptor interceptor(Class<?> k, Optional<Upstream.Logging.Conf> override, Map<Method, Upstream.Logging.Conf> overrides) {
        final var endpoints = Stream.of(k.getMethods())
                .collect(Collectors.toMap(m -> m, m -> new EndpointDescriptor("up", m.getName(), m, null)));
        final var interceptor = new UpstreamLoggingInterceptor(override, overrides);
        interceptor.preprocess(k, expressions, endpoints);
        return interceptor;
    }

    private InvocationContext invocation(Method method) {
        final var converters = new MessageConverters(HttpMessageConverters.forClient().build());
        return new InvocationContext(expressions, rendering, converters, new EndpointDescriptor("up", method.getName(), method, null), new Object[0], "boot", 1, "user", Buffering.BUFFERED);
    }

    private static RequestContext request() {
        return new RequestContext(Instant.EPOCH, HttpMethod.GET, URI.create("http://example.com"), new HttpHeaders(), Map.of(), new byte[0]);
    }

    private static final UpstreamHttpRequestExecution OK = (invocation, request) -> new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", new HttpHeaders(), BodySource.of("body", StandardCharsets.UTF_8), false);

    @Test
    public void methodAnnotationWinsOverTypeAnnotation() throws Exception {
        final var method = LoggedClient.class.getMethod("methodLevel");
        interceptor(LoggedClient.class, Optional.empty(), Map.of()).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(List.of(2), rendering.requestSizes, "the method level configuration must render the request");
        Assertions.assertEquals(List.of(2), rendering.responseSizes, "the method level configuration must render the response");
    }

    @Test
    public void typeAnnotationAppliesToUnannotatedMethods() throws Exception {
        final var method = LoggedClient.class.getMethod("typeLevel");
        interceptor(LoggedClient.class, Optional.empty(), Map.of()).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(List.of(1), rendering.requestSizes, "the type level configuration must apply");
    }

    @Test
    public void globalOverrideWinsOverAnnotations() throws Exception {
        final var method = LoggedClient.class.getMethod("methodLevel");
        interceptor(LoggedClient.class, Optional.of(sized(3)), Map.of()).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(List.of(3), rendering.requestSizes, "the builder override must win over annotations");
    }

    @Test
    public void methodOverrideWinsOverTheGlobalOne() throws Exception {
        final var method = LoggedClient.class.getMethod("methodLevel");
        interceptor(LoggedClient.class, Optional.of(sized(3)), Map.of(method, sized(4))).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(List.of(4), rendering.requestSizes, "the per method override must win over the global one");
    }

    @Test
    public void overrideEnablesLoggingOfUnannotatedEndpoints() throws Exception {
        final var method = UnloggedClient.class.getMethod("unlogged");
        interceptor(UnloggedClient.class, Optional.of(sized(3)), Map.of()).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(List.of(3), rendering.requestSizes, "an override must log endpoints without annotations");
    }

    @Test
    public void unannotatedEndpointsAreNotLogged() throws Exception {
        final var method = UnloggedClient.class.getMethod("unlogged");
        final var got = interceptor(UnloggedClient.class, Optional.empty(), Map.of()).intercept(invocation(method), request(), OK);
        Assertions.assertEquals(HttpStatus.OK, got.status(), "the response must be passed through");
        Assertions.assertTrue(rendering.requestSizes.isEmpty() && rendering.responseSizes.isEmpty(), "nothing must be rendered without a configuration");
    }

    @Test
    public void failuresAreRethrownWithoutRenderingAResponse() throws Exception {
        final var method = LoggedClient.class.getMethod("typeLevel");
        final var interceptor = interceptor(LoggedClient.class, Optional.empty(), Map.of());
        final var invocation = invocation(method);
        final var request = request();
        final UpstreamHttpRequestExecution failing = (i, r) -> {
            throw new IOException("boom");
        };
        Assertions.assertThrows(IOException.class, () -> interceptor.intercept(invocation, request, failing), "the failure must reach the caller");
        Assertions.assertEquals(List.of(1), rendering.requestSizes, "the request must be logged before the failure");
        Assertions.assertTrue(rendering.responseSizes.isEmpty(), "no response must be rendered on failure");
    }
}
