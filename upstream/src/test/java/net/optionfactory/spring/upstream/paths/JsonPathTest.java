package net.optionfactory.spring.upstream.paths;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class JsonPathTest {

    private static final MessageConverters CONVERTERS = new MessageConverters(HttpMessageConverters.forClient()
            .addCustomConverter(new JacksonJsonHttpMessageConverter(new JsonMapper()))
            .build());

    private static HttpHeaders json() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static ResponseContext response(BodySource body) {
        return new ResponseContext(Instant.now(), HttpStatus.OK, "OK", json(), body, false);
    }

    private static JsonPath path(String body) {
        return new JsonPath(CONVERTERS, response(BodySource.of(body, StandardCharsets.UTF_8)));
    }

    @Test
    public void findsAFieldByNameAtAnyDepth() {
        final var got = path("""
                {"outcome": {"detail": {"success": true}}}
                """).path("success");
        Assertions.assertTrue(got.asBoolean(), "a field nested at any depth must be found by its name");
    }

    @Test
    public void aMissingFieldYieldsAMissingNode() {
        final var got = path("""
                {"outcome": {}}
                """).path("success");
        Assertions.assertTrue(got.isMissingNode(), "a field that is not there must yield a MissingNode");
        Assertions.assertFalse(got.asBoolean(), "a MissingNode must read as false, so that expressions do not fail");
    }

    @Test
    public void aBodyThatIsNotJsonYieldsAMissingNode() {
        final var got = path("<success>true</success>").path("success");
        Assertions.assertTrue(got.isMissingNode(), "a body that cannot be parsed must yield a MissingNode instead of failing");
    }

    @Test
    public void anUnbufferedBodyYieldsAMissingNode() {
        final var streamed = new MockClientHttpResponse(HttpStatus.OK, "OK", json(), new ByteArrayResource("{\"success\": true}".getBytes(StandardCharsets.UTF_8)));
        final var got = new JsonPath(CONVERTERS, response(BodySource.of(streamed, Buffering.UNBUFFERED))).path("success");
        Assertions.assertTrue(got.isMissingNode(), "a body that is not buffered must not be consumed, and yield a MissingNode");
    }

    @Test
    public void theBoundHandleLooksUpTheResponse() throws Throwable {
        final var handle = JsonPath.boundMethodHandle(CONVERTERS, response(BodySource.of("{\"code\": 42}", StandardCharsets.UTF_8)));
        final var got = (JsonNode) handle.invokeWithArguments("code");
        Assertions.assertEquals(42, got.asInt(), "the bound handle must look fields up in the bound response");
    }
}
