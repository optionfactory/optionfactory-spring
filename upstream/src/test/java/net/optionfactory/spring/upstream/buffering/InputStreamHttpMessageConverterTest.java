package net.optionfactory.spring.upstream.buffering;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.http.client.MockClientHttpResponse;

public class InputStreamHttpMessageConverterTest {

    @Test
    public void readsTheBodyStreamItselfWhateverTheMediaType() throws IOException {
        final var body = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
        final var converter = new InputStreamHttpMessageConverter();
        Assertions.assertTrue(converter.canRead(InputStream.class, MediaType.valueOf("application/x-anything")), "any media type must be readable as an InputStream");
        Assertions.assertSame(body, converter.read(InputStream.class, new MockHttpInputMessage(body)), "the body stream must be handed over unread and unwrapped");
    }

    @Test
    public void onlyTheInputStreamTypeItselfIsSupported() {
        final var converter = new InputStreamHttpMessageConverter();
        Assertions.assertFalse(converter.canRead(ByteArrayInputStream.class, MediaType.APPLICATION_OCTET_STREAM), "InputStream subclasses must not be claimed");
        Assertions.assertFalse(converter.canRead(String.class, MediaType.APPLICATION_OCTET_STREAM), "other types must not be claimed");
    }

    @Test
    public void writesOnlyWithoutASpecificContentType() {
        final var converter = new InputStreamHttpMessageConverter();
        Assertions.assertTrue(converter.canWrite(InputStream.class, null), "an InputStream must be writable when no content type is requested");
        Assertions.assertFalse(converter.canWrite(InputStream.class, MediaType.APPLICATION_OCTET_STREAM), "the converter declares no media type, so a specific one cannot be written");
    }

    @Test
    public void writesByTransferringTheStream() throws IOException {
        final var out = new MockHttpOutputMessage();
        new InputStreamHttpMessageConverter().write(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), MediaType.APPLICATION_OCTET_STREAM, out);
        Assertions.assertEquals("content", out.getBodyAsString(StandardCharsets.UTF_8), "the whole stream must be copied to the output");
    }

    @Test
    public void closingTheResponseStreamClosesTheResponse() throws IOException {
        final var closed = new boolean[1];
        final var response = new MockClientHttpResponse("content".getBytes(StandardCharsets.UTF_8), HttpStatus.OK) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        final var is = new HttpInputMessageInputStream(response);
        Assertions.assertEquals("content", new String(is.readAllBytes(), StandardCharsets.UTF_8), "the stream must read the response body");
        Assertions.assertFalse(closed[0], "reading must not close the response");
        is.close();
        Assertions.assertTrue(closed[0], "closing the stream must close the response");
    }
}
