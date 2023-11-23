package net.optionfactory.spring.upstream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.FastByteArrayOutputStream;

public interface UpstreamHttpInterceptor {

    default void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
    }

    default ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        return execution.execute(request, body);
    }

    public static record InvocationContext(
            String upstream,
            HttpMessageConverters converters,
            InstantSource clock,
            Instant requestedAt,
            String endpoint,
            Method method,
            Object[] arguments,
            String boot,
            Object principal,
            long request) {

    }

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
                    return HttpHeaders.EMPTY;
                }
            }, type, mediaType);
        }

        public <T> T convert(HttpInputMessage im, Class<T> type, MediaType mediaType) throws IOException {
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
                    return HttpHeaders.EMPTY;
                }

            }, type, mediaType);
            return baos.toByteArrayUnsafe();
        }

        public void convert(Object value, HttpOutputMessage om, Class<?> type, MediaType mediaType) throws IOException {
            final HttpMessageConverter converter = all().stream()
                    .filter(c -> c.canWrite(type, mediaType))
                    .findFirst()
                    .orElseThrow();

            converter.write(value, mediaType, om);
        }
    }
}
