package net.optionfactory.spring.pem;

import net.optionfactory.spring.pem.Pem;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.junit.Assert;
import org.junit.Test;

public class PemUnmarshalingTest {

    private InputStream is(String src) {
        return new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void canUnmarshalPkcs8PrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS8_CLEARTEXT;
        final var pk = Pem.privateKey(is(src), null);
        Assert.assertNotNull(pk);

    }

    @Test
    public void canUnmarshalX509Certificate() throws GeneralSecurityException {
        final var src = TestData.CERTIFICATE_X509;
        final var pk = Pem.certificate(is(src));
        Assert.assertNotNull(pk);

    }

    @Test
    public void canUnmarshalPkcs8EncryptedPrivateKey() throws GeneralSecurityException {
        //openssl genrsa -aes256 -out asd.pem 
        //only aes is supported by jdk
        final var src = TestData.PRIVATE_KEY_PKCS8_ENCRYPTED;
        final var pk = Pem.privateKey(is(src), "changeit".toCharArray());
        Assert.assertNotNull(pk);

    }

    @Test
    public void canUnmarshalPkc1PrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS1;
        final var pk = Pem.privateKey(is(src), null);
        Assert.assertNotNull(pk);

    }

}
