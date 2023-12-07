package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Instant;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

public record ResponseContext(
        Instant at,
        HttpStatusCode status,
        String statusText,
        HttpHeaders headers,
        BodySource body) {

    public interface BodySource {

        InputStream inputStream();

        default byte[] bytes() {
            final var in = inputStream();
            if (in == null) {
                return new byte[0];
            }

            try (in) {
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        public static BodySource of(ClientHttpResponse cr) {
            return () -> {
                try {
                    return cr.getBody();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            };
        }

        public static BodySource of(InputStreamSource cr) {
            return () -> {
                try {
                    return cr.getInputStream();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            };
        }

        public static BodySource of(byte[] bs) {
            return new BodySource() {
                @Override
                public InputStream inputStream() {
                    return new ByteArrayInputStream(bs);
                }

                @Override
                public byte[] bytes() {
                    return bs;
                }

            };
        }

        public static BodySource of(String str, Charset cs) {
            return of(str.getBytes(cs));
        }
    }
}
