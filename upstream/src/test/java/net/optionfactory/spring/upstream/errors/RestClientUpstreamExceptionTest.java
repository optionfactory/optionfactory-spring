package net.optionfactory.spring.upstream.errors;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

public class RestClientUpstreamExceptionTest {

    private static final MessageConverters JSON = new MessageConverters(HttpMessageConverters.forClient()
            .addCustomConverter(new JacksonJsonHttpMessageConverter(new JsonMapper()))
            .build());

    @Test
    public void bodyCharsetComesFromTheContentType() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/plain;charset=ISO-8859-1"));
        final var body = "caffè".getBytes(StandardCharsets.ISO_8859_1);
        final var ex = new RestClientUpstreamException(JSON, "up", "ep", "reason", HttpStatus.BAD_REQUEST, "Bad Request", headers, body);
        Assertions.assertEquals("caffè", ex.getResponseBodyAsString(), "the body must be decoded with the content type charset");
    }

    @Test
    public void missingHeadersAndBodyAreTolerated() {
        final var ex = new RestClientUpstreamException(JSON, "up", "ep", "reason", HttpStatus.BAD_REQUEST, "Bad Request", null, null);
        Assertions.assertNull(ex.getResponseBodyAs(Map.class), "a missing body must convert to null");
        Assertions.assertEquals("", ex.getResponseBodyAsString(), "a missing body must read as empty");
    }

    @Test
    public void bodyIsConvertedWithTheGivenConverters() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var ex = new RestClientUpstreamException(JSON, "up", "ep", "reason", HttpStatus.BAD_REQUEST, "Bad Request", headers, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(Map.of("a", 1), ex.getResponseBodyAs(Map.class), "the body must be converted by the matching converter");
    }
}
