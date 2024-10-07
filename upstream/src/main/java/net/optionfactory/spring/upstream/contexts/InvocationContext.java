package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.FastByteArrayOutputStream;

public record InvocationContext(
        Expressions expressions,
        HttpMessageConverters converters,
        EndpointDescriptor endpoint,
        Object[] arguments,
        String boot,
        long id,
        Object principal) {

    public record HttpMessageConverters(List<HttpMessageConverter<?>> all) {

        public <T> T convert(byte[] bytes, Class<T> type, MediaType mediaType) throws IOException {
            return convert(new ByteArrayInputStream(bytes), type, mediaType);
        }

        public <T> T convert(InputStream is, Class<T> type, MediaType mediaType) throws IOException {
            return convert(new HttpInputMessage() {
                @Override
                public InputStream getBody() throws IOException {
                    return is;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return new HttpHeaders();
                }
            }, type, mediaType);
        }

        public <T> T convert(HttpInputMessage im, Class<T> type, MediaType mediaType) throws IOException {
            @SuppressWarnings("unchecked")
            final HttpMessageConverter<T> converter = (HttpMessageConverter) all().stream()
                    .filter(c -> c.canRead(type, mediaType))
                    .findFirst()
                    .orElseThrow();
            return converter.read(type, im);
        }

        public byte[] convert(Object value, Class<?> type, MediaType mediaType) throws IOException {
            final var baos = new FastByteArrayOutputStream();
            convert(value, new HttpOutputMessage() {
                @Override
                public OutputStream getBody() throws IOException {
                    return baos;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return new HttpHeaders();
                }

            }, type, mediaType);
            return baos.toByteArrayUnsafe();
        }

        @SuppressWarnings("unchecked")
        public void convert(Object value, HttpOutputMessage om, Class<?> type, MediaType mediaType) throws IOException {

            final HttpMessageConverter converter = all().stream()
                    .filter(c -> c.canWrite(type, mediaType))
                    .findFirst()
                    .orElseThrow();

            converter.write(value, mediaType, om);
        }
    }
}
