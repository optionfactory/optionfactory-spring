package net.optionfactory.spring.pem;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PemUnmarshalingTest {

    private InputStream is(String src) {
        return new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void canUnmarshalPkcs8PrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS8_CLEARTEXT;
        final var pk = Pem.privateKey(is(src), null);
        Assertions.assertNotNull(pk);

    }

    @Test
    public void canUnmarshalX509Certificate() throws GeneralSecurityException {
        final var src = TestData.CERTIFICATE_X509;
        final var pk = Pem.certificate(is(src));
        Assertions.assertNotNull(pk);

    }

    @Test
    public void canUnmarshalPkcs8EncryptedPrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS8_ENCRYPTED;
        final var pk = Pem.privateKey(is(src), "changeit".toCharArray());
        Assertions.assertNotNull(pk);
    }

    @Test
    public void canUnmarshalPkc1PrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS1;
        final var pk = Pem.privateKey(is(src), null);
        Assertions.assertNotNull(pk);

    }

}
