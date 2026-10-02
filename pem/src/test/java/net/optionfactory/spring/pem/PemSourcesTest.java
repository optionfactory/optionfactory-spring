package net.optionfactory.spring.pem;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;

public class PemSourcesTest {

    private static ByteArrayResource resource(String src) {
        return new ByteArrayResource(src.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsFromAnInputStreamSource() {
        Assertions.assertEquals("RSA", PemSources.privateKey(resource(TestData.PRIVATE_KEY_PKCS1), null).getAlgorithm(), "the key is read from the resource");
        Assertions.assertEquals("X.509", PemSources.certificate(resource(TestData.CERTIFICATE_X509)).getType(), "the certificate is read from the resource");
        Assertions.assertDoesNotThrow(() -> PemSources.keyStore(resource(TestData.CERTIFICATE_X509_CHAIN)).size(), "the keystore is read from the resource");
    }

    @Test
    public void closesTheStreamItOpens() {
        final var closed = new AtomicBoolean(false);
        final InputStreamSource iss = () -> new FilterInputStream(resource(TestData.CERTIFICATE_X509).getInputStream()) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        PemSources.certificate(iss);
        Assertions.assertTrue(closed.get(), "the stream opened from the source is closed after reading");
    }

    @Test
    public void wrapsIoFailuresOfTheSource() {
        final InputStreamSource failing = () -> {
            throw new IOException("unreadable");
        };
        final var ex = Assertions.assertThrows(PemException.class, () -> PemSources.keyStore(failing), "an unreadable source is reported as a PemException");
        Assertions.assertInstanceOf(IOException.class, ex.getCause(), "the io failure is kept as the cause");
    }
}
