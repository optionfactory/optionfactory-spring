package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.optionfactory.spring.upstream.buffering.Buffering;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/// A response received, as seen by interceptors, error handlers and expressions (`#response`).
///
/// Whether the body can be read more than once depends on the
/// [net.optionfactory.spring.upstream.buffering.Buffering] of the endpoint:
/// [BodySource#forInspection] is the safe way to look at it without consuming a streamed body.
///
/// @param at when the response was received, according to the client clock
/// @param status the response status
/// @param statusText the response status text
/// @param headers the response headers
/// @param body the response body
/// @param alert whether an alert was raised for this response
public record ResponseContext(
        Instant at,
        HttpStatusCode status,
        String statusText,
        HttpHeaders headers,
        BodySource body,
        boolean alert) {

    /// @return the response body
    public BodySource body() {
        return body;
    }

    /// @return a copy whose body no longer depends on the connection, see [BodySource#detached]
    public ResponseContext detached() {
        return new ResponseContext(at, status, statusText, headers, body.detached(), alert);
    }

    /// @return a copy flagged as alerted, so that the same response is not reported twice
    public ResponseContext withAlert() {
        return new ResponseContext(at, status, statusText, headers, body, true);
    }

    /// The body of a response, possibly backed by the connection.
    public interface BodySource extends InputStreamSource {

        /// @return the body stream; for a connection backed body, the response body itself
        @Override
        InputStream getInputStream();

        /// @return a body held in memory, or an `<unavailable>` placeholder for the bodies that are not
        /// buffered, which are left unread
        BodySource detached();

        /// Gives access to the body for logs, alerts and conditions, without consuming a streamed body.
        ///
        /// @param throwIfUnavailable whether a body that is not buffered fails the inspection
        /// @return this body when it can be read more than once, an `<unavailable>` placeholder otherwise
        /// @throws IllegalStateException when the body is not buffered and `throwIfUnavailable` is set
        BodySource forInspection(boolean throwIfUnavailable);

        /// @return the whole body, read and closed for a connection backed body
        byte[] bytes();

        /// @param cr the response
        /// @param buffering how the response is buffered
        /// @return a body backed by the response
        public static BodySource of(ClientHttpResponse cr, Buffering buffering) {
            return new ClientHttpResponseBodySource(cr, buffering);
        }

        /// @param bs the body
        /// @return an in-memory body
        public static BodySource of(byte[] bs) {
            return new ByteArrayBodySource(bs);
        }

        /// @param str the body
        /// @param cs the encoding of the body
        /// @return an in-memory body
        public static BodySource of(String str, Charset cs) {
            return new ByteArrayBodySource(str.getBytes(cs));
        }
    }

    /// A body backed by a response, readable more than once only when the response is buffered.
    public static class ClientHttpResponseBodySource implements BodySource {

        private final ClientHttpResponse chr;
        private final Buffering buffering;

        /// @param chr the response
        /// @param buffering how the response is buffered
        public ClientHttpResponseBodySource(ClientHttpResponse chr, Buffering buffering) {
            this.chr = chr;
            this.buffering = buffering;
        }

        /// @return the response body
        /// @throws java.io.UncheckedIOException when the body cannot be obtained
        @Override
        public InputStream getInputStream() {
            try {
                return chr.getBody();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /// @return an in-memory copy of a buffered body, read from the response, or an `<unavailable>`
        /// placeholder leaving a body that is not buffered unread
        /// @throws java.io.UncheckedIOException when the body cannot be read
        @Override
        public ResponseContext.BodySource detached() {
            if (buffering != Buffering.BUFFERED ) {
                return new ByteArrayBodySource("<unavailable>".getBytes(StandardCharsets.UTF_8));
            }
            try {
                return BodySource.of(chr.getBody().readAllBytes());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /// @param throwIfUnavailable whether a body that is not buffered fails the inspection
        /// @return this body when buffered, an `<unavailable>` placeholder otherwise
        /// @throws IllegalStateException when the body is not buffered and `throwIfUnavailable` is set
        @Override
        public ResponseContext.BodySource forInspection(boolean throwIfUnavailable) {
            if (buffering != Buffering.BUFFERED) {
                if (throwIfUnavailable) {
                    throw new IllegalStateException("trying to inspect an unavailable body");
                }
                return new ByteArrayBodySource("<unavailable>".getBytes(StandardCharsets.UTF_8));
            }
            return this;
        }

        /// @return the whole body, an empty array when the response has none
        /// @throws java.io.UncheckedIOException when the body cannot be read
        @Override
        public byte[] bytes() {
            final var in = getInputStream();
            if (in == null) {
                return new byte[0];
            }
            try (in) {
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

    }

    /// A body held in memory, readable any number of times.
    public static class ByteArrayBodySource implements BodySource {

        private final byte[] data;

        /// @param data the body, not copied
        public ByteArrayBodySource(byte[] data) {
            this.data = data;
        }

        /// @return a new stream over the body
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }

        /// @return this body, already detached
        @Override
        public ResponseContext.BodySource detached() {
            return this;
        }

        /// @param throwIfUnavailable unused, an in-memory body is always available
        /// @return this body
        @Override
        public ResponseContext.BodySource forInspection(boolean throwIfUnavailable) {
            return this;
        }

        /// @return the body itself, not a copy
        @Override
        public byte[] bytes() {
            return data;
        }

    }
}
