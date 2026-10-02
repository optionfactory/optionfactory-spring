package net.optionfactory.spring.upstream.buffering;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class BufferingTest {

    public interface Endpoints {

        InputStream inputStream();

        Stream<String> stream();

        ResponseEntity<InputStream> entityOfInputStream();

        ResponseEntity<Stream<String>> entityOfStream();

        String string();

        List<String> list();

        ResponseEntity<String> entityOfString();

        void nothing();
    }

    private static Buffering of(String methodName) throws NoSuchMethodException {
        return Buffering.responseBufferingFromMethod(Endpoints.class.getMethod(methodName));
    }

    @Test
    public void streamingReturnTypesAreNotBuffered() throws NoSuchMethodException {
        Assertions.assertEquals(Buffering.UNBUFFERED_STREAMING, of("inputStream"), "an InputStream result must be streamed");
        Assertions.assertEquals(Buffering.UNBUFFERED_STREAMING, of("stream"), "a Stream result must be streamed");
        Assertions.assertEquals(Buffering.UNBUFFERED_STREAMING, of("entityOfInputStream"), "a ResponseEntity of InputStream must be streamed");
        Assertions.assertEquals(Buffering.UNBUFFERED_STREAMING, of("entityOfStream"), "a ResponseEntity of Stream must be streamed");
    }

    @Test
    public void everyOtherReturnTypeIsBuffered() throws NoSuchMethodException {
        Assertions.assertEquals(Buffering.BUFFERED, of("string"), "a String result must be buffered");
        Assertions.assertEquals(Buffering.BUFFERED, of("list"), "a List result is fully materialized, hence buffered");
        Assertions.assertEquals(Buffering.BUFFERED, of("entityOfString"), "a ResponseEntity of a non streaming type must be buffered");
        Assertions.assertEquals(Buffering.BUFFERED, of("nothing"), "a void result must be buffered");
    }
}
