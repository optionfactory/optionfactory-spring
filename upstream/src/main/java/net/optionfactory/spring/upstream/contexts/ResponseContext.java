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

public record ResponseContext(
        Instant at,
        HttpStatusCode status,
        String statusText,
        HttpHeaders headers,
        BodySource body,
        boolean alert) {

    public BodySource body() {
        return body;
    }

    public ResponseContext detached() {
        return new ResponseContext(at, status, statusText, headers, body.detached(), alert);
    }

    public ResponseContext withAlert() {
        return new ResponseContext(at, status, statusText, headers, body, true);
    }

    public interface BodySource extends InputStreamSource {

        @Override
        InputStream getInputStream();

        BodySource detached();

        BodySource forInspection(boolean throwIfUnavailable);

        byte[] bytes();

        public static BodySource of(ClientHttpResponse cr, Buffering buffering) {
            return new ClientHttpResponseBodySource(cr, buffering);
        }

        public static BodySource of(byte[] bs) {
            return new ByteArrayBodySource(bs);
        }

        public static BodySource of(String str, Charset cs) {
            return new ByteArrayBodySource(str.getBytes(cs));
        }
    }

    public static class ClientHttpResponseBodySource implements BodySource {

        private final ClientHttpResponse chr;
        private final Buffering buffering;

        public ClientHttpResponseBodySource(ClientHttpResponse chr, Buffering buffering) {
            this.chr = chr;
            this.buffering = buffering;
        }

        @Override
        public InputStream getInputStream() {
            try {
                return chr.getBody();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public ResponseContext.BodySource detached() {
            if (buffering == Buffering.UNBUFFERED) {
                return new ByteArrayBodySource("<unavailable>".getBytes(StandardCharsets.UTF_8));
            }
            try {
                return BodySource.of(chr.getBody().readAllBytes());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public ResponseContext.BodySource forInspection(boolean throwIfUnavailable) {
            if (buffering == Buffering.UNBUFFERED) {
                if (throwIfUnavailable) {
                    throw new IllegalStateException("trying to inspect an unavailable body");
                }
                return new ByteArrayBodySource("<unavailable>".getBytes(StandardCharsets.UTF_8));
            }
            return this;
        }

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

    public static class ByteArrayBodySource implements BodySource {

        private final byte[] data;

        public ByteArrayBodySource(byte[] data) {
            this.data = data;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }

        @Override
        public ResponseContext.BodySource detached() {
            return this;
        }

        @Override
        public ResponseContext.BodySource forInspection(boolean throwIfUnavailable) {
            return this;
        }

        @Override
        public byte[] bytes() {
            return data;
        }

    }
}
