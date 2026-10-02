package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
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

/// One invocation of a client method, available to every component taking part in it and to
/// expressions as `#invocation`.
///
/// It is created when the method is invoked and is held by the invoking thread until the method
/// returns, so components called on another thread do not see it.
///
/// @param expressions the expressions of the client
/// @param rendering renders payloads for logs and alerts
/// @param converters the message converters of the client
/// @param endpoint the invoked endpoint
/// @param arguments the method arguments, `@Upstream.Context` and `@Upstream.Principal` ones included
/// @param boot identifies the JVM run, so that invocation ids from different runs can be told apart
/// in the logs
/// @param id the invocation id, increasing across all the clients of the JVM
/// @param principal the principal of the invocation, or `null`, see
/// [net.optionfactory.spring.upstream.Upstream.Principal]
/// @param buffering how the response of the endpoint is buffered
public record InvocationContext(
        Expressions expressions,
        PayloadsRendering rendering,
        MessageConverters converters,
        EndpointDescriptor endpoint,
        Object[] arguments,
        String boot,
        long id,
        Object principal,
        Buffering buffering) {

    /// The message converters of a client, usable outside of the `RestClient`: to read an error body,
    /// or a body inspected by an expression (`#json_path`).
    ///
    /// Converters are tried in order, the first one able to read or write the type and media type is
    /// used, as `RestClient` does.
    ///
    /// @param all the converters
    public record MessageConverters(HttpMessageConverters all) {

        /// @param inputMessage the message to read, whose `Content-Type` selects the converter
        /// @param type the target type
        /// @return the converted body
        /// @throws IOException when the body cannot be read
        /// @throws org.springframework.web.client.RestClientException when no converter can read the type
        /// and content type
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

        /// @param body the body, possibly `null`
        /// @param type the target type
        /// @param headers the headers of the body, whose `Content-Type` selects the converter; possibly
        /// `null`
        /// @return the converted body, `null` when the body is `null` or empty
        /// @throws org.springframework.web.client.RestClientException when no converter can read the type
        /// and content type, or the body cannot be read
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

        /// @param <T> the target type
        /// @param im the message to read, whose `Content-Type` selects the converter
        /// @param type the target type
        /// @return the converted body
        /// @throws IOException when the body cannot be read
        /// @throws org.springframework.web.client.RestClientException when no converter can read the type
        /// and content type
        @SuppressWarnings("unchecked")
        public <T> T convert(HttpInputMessage im, Class<T> type) throws IOException {
            return (T) convert(im, ResolvableType.forClass(type));
        }

        /// @param <T> the target type
        /// @param is the body
        /// @param type the target type
        /// @param headers the headers of the body, whose `Content-Type` selects the converter; possibly
        /// `null`
        /// @return the converted body
        /// @throws IOException when the body cannot be read
        /// @throws org.springframework.web.client.RestClientException when no converter can read the type
        /// and content type
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

        /// @param <T> the target type
        /// @param bytes the body, possibly `null`
        /// @param type the target type
        /// @param headers the headers of the body, whose `Content-Type` selects the converter; possibly
        /// `null`
        /// @return the converted body, `null` when the body is `null` or empty
        /// @throws IOException when the body cannot be read
        /// @throws org.springframework.web.client.RestClientException when no converter can read the type
        /// and content type
        public <T> T convert(byte[] bytes, Class<T> type, HttpHeaders headers) throws IOException {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return convert(new ByteArrayInputStream(bytes), type, headers);
        }

        /// @param value the value to write
        /// @param type the type the converter is chosen for
        /// @param mediaType the media type to write
        /// @return the written bytes
        /// @throws IOException when the value cannot be written
        /// @throws java.util.NoSuchElementException when no converter can write the type and media type
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

        /// @param value the value to write
        /// @param om the message to write to
        /// @param type the type the converter is chosen for
        /// @param mediaType the media type to write
        /// @throws IOException when the value cannot be written
        /// @throws java.util.NoSuchElementException when no converter can write the type and media type
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
