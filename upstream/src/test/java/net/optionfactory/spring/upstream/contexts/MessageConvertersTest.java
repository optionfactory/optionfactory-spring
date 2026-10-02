package net.optionfactory.spring.upstream.contexts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

public class MessageConvertersTest {

    private static MessageConverters json() {
        return new MessageConverters(HttpMessageConverters.forClient()
                .addCustomConverter(new JacksonJsonHttpMessageConverter(new JsonMapper()))
                .build());
    }

    private static HttpHeaders contentType(MediaType mediaType) {
        final var headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return headers;
    }

    @Test
    public void readsBytesWithTheConverterMatchingTheContentType() throws IOException {
        final var got = json().convert("{\"a\":\"b\"}".getBytes(StandardCharsets.UTF_8), Map.class, contentType(MediaType.APPLICATION_JSON));
        Assertions.assertEquals(Map.of("a", "b"), got, "a json body must be read by the json converter");
    }

    @Test
    public void readsGenericTypes() {
        final var type = ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class);
        final var got = json().convert("{\"a\":1}".getBytes(StandardCharsets.UTF_8), type, contentType(MediaType.APPLICATION_JSON));
        Assertions.assertEquals(Map.of("a", 1), got, "a generic target type must be honored");
    }

    @Test
    public void emptyOrMissingBodiesConvertToNull() throws IOException {
        Assertions.assertNull(json().convert(new byte[0], Map.class, contentType(MediaType.APPLICATION_JSON)), "an empty body must convert to null");
        Assertions.assertNull(json().convert((byte[]) null, Map.class, contentType(MediaType.APPLICATION_JSON)), "a missing body must convert to null");
        Assertions.assertNull(json().convert(new byte[0], ResolvableType.forClass(Map.class), null), "an empty body must convert to null for a resolvable type too");
    }

    @Test
    public void bodyWithoutAMatchingConverterIsRejected() {
        final var converters = new MessageConverters(HttpMessageConverters.forClient().addCustomConverter(new StringHttpMessageConverter()).build());
        final var body = "{}".getBytes(StandardCharsets.UTF_8);
        final var type = ResolvableType.forClass(Map.class);
        final var headers = contentType(MediaType.APPLICATION_JSON);
        Assertions.assertThrows(RestClientException.class, () -> converters.convert(body, type, headers), "a body no converter can read must fail the conversion");
    }

    @Test
    public void writesWithTheFirstConverterAcceptingTheValue() throws IOException {
        final var got = json().convert(Map.of("a", "b"), Map.class, MediaType.APPLICATION_JSON);
        Assertions.assertEquals("{\"a\":\"b\"}", new String(got, StandardCharsets.UTF_8), "a map must be written as json");
    }

    @Test
    public void writingWithoutAMatchingConverterFails() {
        final var converters = new MessageConverters(HttpMessageConverters.forClient().addCustomConverter(new StringHttpMessageConverter()).build());
        final var value = Map.of("a", "b");
        Assertions.assertThrows(NoSuchElementException.class, () -> converters.convert(value, Map.class, MediaType.APPLICATION_JSON), "a value no converter can write must fail");
    }
}
