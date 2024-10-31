package net.optionfactory.spring.upstream.contexts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Instant;
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

    public ResponseContext detached() {
        return new ResponseContext(at, status, statusText, headers, body.detached(), alert);
    }

    public ResponseContext witAlert() {
        return new ResponseContext(at, status, statusText, headers, body, true);
    }

    public interface BodySource {

        InputStream inputStream();

        BodySource detached();

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
            return new BodySource() {
                @Override
                public InputStream inputStream() {
                    try {
                        return cr.getBody();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }

                @Override
                public BodySource detached() {
                    try {
                        return BodySource.of(cr.getBody().readAllBytes());
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
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
                public BodySource detached() {
                    return this;
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
