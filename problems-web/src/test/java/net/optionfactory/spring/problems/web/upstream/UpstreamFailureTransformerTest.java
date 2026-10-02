package net.optionfactory.spring.problems.web.upstream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import net.optionfactory.spring.problems.web.upstream.UpstreamProblems.MapMode;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

public class UpstreamFailureTransformerTest {

    private static final MessageConverters CONVERTERS = new MessageConverters(HttpMessageConverters.forClient().registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter()).build());

    public static class Handlers {

        public void plain() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.UNPROCESSABLE_CONTENT)
        public void forwarding() {
        }

        @UpstreamProblems.Forward(upstream = "warehouse", target = HttpStatus.UNPROCESSABLE_CONTENT)
        public void forwardingTheWarehouseOnly() {
        }

        @UpstreamProblems.Forward(source = HttpStatus.CONFLICT, target = HttpStatus.UNPROCESSABLE_CONTENT)
        public void forwardingConflictsOnly() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.UNPROCESSABLE_CONTENT, problems = false)
        public void forwardingTheStatusOnly() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.BAD_REQUEST)
        @UpstreamProblems.MapContext(mode = MapMode.STRING_ALL, source = "item", target = "line")
        public void mappingEveryOccurrence() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.BAD_REQUEST)
        @UpstreamProblems.MapContext(mode = MapMode.REGEX_FIRST, source = "items\\.(\\d+)", target = "lines[$1]")
        public void mappingARegex() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.BAD_REQUEST)
        @UpstreamProblems.MapContext(source = "order.", target = "")
        @UpstreamProblems.MapContext(source = "items", target = "lines")
        public void mappingTwice() {
        }

        @UpstreamProblems.Forward(target = HttpStatus.BAD_REQUEST)
        @UpstreamProblems.MapContext(endpoint = "other-endpoint", source = "items", target = "lines")
        public void mappingAnotherEndpoint() {
        }
    }

    private final UpstreamFailureTransformer transformer = new UpstreamFailureTransformer();

    private static RestClientUpstreamException upstreamFailure(String upstream, HttpStatus status, String context) {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/failures+json"));
        final var body = "[{\"type\": \"FIELD_ERROR\", \"context\": \"%s\", \"reason\": \"must not be null\"}]".formatted(context);
        return new RestClientUpstreamException(CONVERTERS, upstream, "endpoint", "reason", status, status.getReasonPhrase(), headers, body.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpStatusAndProblems badGateway() {
        return new HttpStatusAndProblems(HttpStatus.BAD_GATEWAY, List.of(Problem.upstream(null, "upstream failure", null)));
    }

    private HttpStatusAndProblems transform(String handlerName, Exception ex) throws NoSuchMethodException {
        final var handler = new HandlerMethod(new Handlers(), Handlers.class.getMethod(handlerName));
        return transformer.transform(badGateway(), new MockHttpServletRequest(), new MockHttpServletResponse(), handler, ex);
    }

    private static List<String> contexts(HttpStatusAndProblems saps) {
        return saps.problems().stream().map(p -> p.context).toList();
    }

    @Test
    public void anotherExceptionIsLeftUnchanged() throws NoSuchMethodException {
        final var got = transform("forwarding", new IllegalStateException("a bug"));
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY, got.status(), "only upstream failures must be forwarded");
    }

    @Test
    public void aHandlerWithoutDeclarationsIsLeftUnchanged() throws NoSuchMethodException {
        final var got = transform("plain", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY, got.status(), "without @Forward the upstream failure must stay a bad gateway");
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, got.problems().get(0).type, "without @Forward the upstream problems must not be exposed");
    }

    @Test
    public void aMatchingFailureIsForwardedWithTheUpstreamsProblems() throws NoSuchMethodException {
        final var got = transform("forwarding", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, got.status(), "a forwarded failure must be answered with the target status");
        Assertions.assertEquals(List.of("items.0"), contexts(got), "a forwarded failure must carry the upstream's problems");
    }

    @Test
    public void aFailureOfAnotherUpstreamIsNotForwarded() throws NoSuchMethodException {
        final var got = transform("forwardingTheWarehouseOnly", upstreamFailure("billing", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY, got.status(), "a failure of an upstream other than the one named must not be forwarded");
    }

    @Test
    public void aFailureWithAnotherStatusIsNotForwarded() throws NoSuchMethodException {
        final var got = transform("forwardingConflictsOnly", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(HttpStatus.BAD_GATEWAY, got.status(), "a failure with a status other than the source must not be forwarded");
    }

    @Test
    public void aStatusOnlyForwardKeepsTheResolversProblems() throws NoSuchMethodException {
        final var got = transform("forwardingTheStatusOnly", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, got.status(), "the target status must be used");
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, got.problems().get(0).type, "with problems = false the upstream's problems must not be exposed");
    }

    @Test
    public void aStringMappingCanReplaceEveryOccurrence() throws NoSuchMethodException {
        final var got = transform("mappingEveryOccurrence", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "item.subitem"));
        Assertions.assertEquals(List.of("line.subline"), contexts(got), "STRING_ALL must replace every literal occurrence");
    }

    @Test
    public void aRegexMappingCanReferToItsGroups() throws NoSuchMethodException {
        final var got = transform("mappingARegex", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.3.sku"));
        Assertions.assertEquals(List.of("lines[3].sku"), contexts(got), "REGEX_FIRST must replace the match, expanding group references");
    }

    @Test
    public void mappingsApplyInDeclarationOrderEachOnThePreviousResult() throws NoSuchMethodException {
        final var got = transform("mappingTwice", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "order.items.0"));
        Assertions.assertEquals(List.of("lines.0"), contexts(got), "both mappings must apply, in order");
    }

    @Test
    public void aMappingForAnotherEndpointIsSkipped() throws NoSuchMethodException {
        final var got = transform("mappingAnotherEndpoint", upstreamFailure("warehouse", HttpStatus.BAD_REQUEST, "items.0"));
        Assertions.assertEquals(List.of("items.0"), contexts(got), "a mapping naming another endpoint must not apply");
    }
}
