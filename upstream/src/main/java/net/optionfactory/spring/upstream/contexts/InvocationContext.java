package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.web.client.RestClientException;

public record InvocationContext(
        Expressions expressions,
        BodyRendering rendering,
        MessageConverters converters,
        EndpointDescriptor endpoint,
        Object[] arguments,
        String boot,
        long id,
        Object principal,
        Buffering buffering) {

    public record MessageConverters(HttpMessageConverters all) {

        public Object convert(HttpInputMessage inputMessage, ResolvableType type) throws IOException {
            final MediaType contentType = inputMessage.getHeaders() != null ? inputMessage.getHeaders().getContentType() : null;
            final Type targetType = type.getType();
            final Class<?> targetClass = type.toClass();

            for (HttpMessageConverter<?> converter : all()) {
                if (converter instanceof SmartHttpMessageConverter<?> smartConverter) {
                    if (smartConverter.canRead(type, contentType)) {
                        return smartConverter.read(type, inputMessage, null);
                    }
                } else if (converter instanceof GenericHttpMessageConverter<?> genericConverter) {
                    if (genericConverter.canRead(targetType, null, contentType)) {
                        return genericConverter.read(targetType, null, inputMessage);
                    }
                } else if (converter.canRead(targetClass, contentType)) {
                    @SuppressWarnings("unchecked")
                    HttpMessageConverter<Object> objectConverter = (HttpMessageConverter<Object>) converter;
                    return objectConverter.read(targetClass, inputMessage);
                }
            }
            throw new RestClientException("No suitable HttpMessageConverter found for response type [" + type + "] and content type [" + contentType + "]");
        }

        public Object convert(byte[] body, ResolvableType type, HttpHeaders headers) {
            if (body == null || body.length == 0) {
                return null;
            }
            try {
                return convert(new HttpInputMessage() {
                    @Override
                    public InputStream getBody() {
                        return new ByteArrayInputStream(body);
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        return headers != null ? headers : HttpHeaders.EMPTY;
                    }
                }, type);
            } catch (IOException ex) {
                throw new RestClientException("Error reading response for type [" + type + "]", ex);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T convert(HttpInputMessage im, Class<T> type) throws IOException {
            return (T) convert(im, ResolvableType.forClass(type));
        }

        public <T> T convert(InputStream is, Class<T> type, HttpHeaders headers) throws IOException {
            return convert(new HttpInputMessage() {
                @Override
                public InputStream getBody() {
                    return is;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers != null ? headers : HttpHeaders.EMPTY;
                }
            }, type);
        }

        public <T> T convert(byte[] bytes, Class<T> type, HttpHeaders headers) throws IOException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return convert(new ByteArrayInputStream(bytes), type, headers);
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

            final HttpMessageConverter converter = (HttpMessageConverter) StreamSupport.stream(all().spliterator(), false)
                    .filter(c -> c.canWrite(type, mediaType))
                    .findFirst()
                    .orElseThrow();

            converter.write(value, mediaType, om);
        }
    }
}
