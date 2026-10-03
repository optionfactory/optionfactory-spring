package net.optionfactory.spring.pem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateCrtKey;
import net.optionfactory.spring.pem.der.DerException;
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
        Assertions.assertNotNull(pk, "a cleartext PKCS#8 key is read without a passphrase");

    }

    @Test
    public void canUnmarshalX509Certificate() throws GeneralSecurityException {
        final var src = TestData.CERTIFICATE_X509;
        final var pk = Pem.certificate(is(src));
        Assertions.assertNotNull(pk, "a CERTIFICATE entry is read as an X.509 certificate");

    }

    @Test
    public void canUnmarshalPkcs8EncryptedPrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS8_ENCRYPTED;
        final var pk = Pem.privateKey(is(src), "changeit".toCharArray());
        Assertions.assertNotNull(pk, "an encrypted PKCS#8 key is decrypted with the right passphrase");
    }

    @Test
    public void canUnmarshalPkc1PrivateKey() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS1;
        final var pk = Pem.privateKey(is(src), null);
        Assertions.assertNotNull(pk, "a PKCS#1 RSA key is read without a passphrase");

    }

    @Test
    public void pkcs1PrivateKeyKeepsItsCrtParameters() {
        final var pk = (RSAPrivateCrtKey) Pem.privateKey(is(TestData.PRIVATE_KEY_PKCS1), null);
        Assertions.assertEquals(1024, pk.getModulus().bitLength(), "the fixture is a 1024 bit RSA key");
        Assertions.assertEquals(pk.getModulus(), pk.getPrimeP().multiply(pk.getPrimeQ()), "the modulus is the product of the parsed primes");
    }

    @Test
    public void cleartextKeyIgnoresThePassphrase() {
        final var pk = Pem.privateKey(is(TestData.PRIVATE_KEY_PKCS8_CLEARTEXT), "whatever".toCharArray());
        Assertions.assertEquals("RSA", pk.getAlgorithm(), "a passphrase given for a cleartext key is ignored");
    }

    @Test
    public void encryptedKeyWithoutPassphraseIsRejected() {
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(TestData.PRIVATE_KEY_PKCS8_ENCRYPTED), null), "an encrypted key needs a passphrase");
        Assertions.assertTrue(ex.getMessage().contains("null passphrase"), "the failure names the missing passphrase");
    }

    @Test
    public void encryptedKeyWithWrongPassphraseIsRejected() {
        Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(TestData.PRIVATE_KEY_PKCS8_ENCRYPTED), "wrong".toCharArray()), "a wrong passphrase fails the decryption");
    }

    @Test
    public void ecPrivateKeysAreNotSupported() {
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(TestData.PRIVATE_KEY_EC), null), "only RSA keys are supported");
        Assertions.assertTrue(ex.getMessage().contains("EC PRIVATE KEY"), "the failure names the unsupported label");
    }

    @Test
    public void privateKeyFromAnEmptySourceFails() {
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(""), null), "a source without entries holds no key");
        Assertions.assertEquals("private key not found", ex.getMessage(), "the failure says no key was found");
    }

    @Test
    public void certificateFromAnEmptySourceFails() {
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.certificate(is("  \n")), "a source without entries holds no certificate");
        Assertions.assertEquals("certificate not found", ex.getMessage(), "the failure says no certificate was found");
    }

    @Test
    public void privateKeyOnlyLooksAtTheFirstEntry() {
        final var src = TestData.CERTIFICATE_X509 + TestData.PRIVATE_KEY_PKCS8_CLEARTEXT;
        Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(src), null), "a key that is not the first entry is not searched for");
    }

    @Test
    public void certificateOfAKeyEntryIsRejected() {
        Assertions.assertThrows(PemException.class, () -> Pem.certificate(is(TestData.PRIVATE_KEY_PKCS8_CLEARTEXT)), "a key entry is not a certificate");
    }

    @Test
    public void malformedPemIsRejected() {
        Assertions.assertThrows(PemException.class, () -> Pem.certificate(is("-----BEGIN CERTIFICATE-----\nMIID\n")), "an entry without its END line is a parse failure");
    }

    @Test
    public void textAroundEntriesIsNotAllowed() {
        Assertions.assertThrows(PemException.class, () -> Pem.certificate(is("# comment\n" + TestData.CERTIFICATE_X509)), "explanatory text that is not metadata is a parse failure");
    }

    @Test
    public void readFailuresFailTheParsingInsteadOfTruncatingIt() {
        final var failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk on fire");
            }
        };
        final var src = new SequenceInputStream(is(TestData.CERTIFICATE_X509), failing);
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.keyStore(src), "a stream failing after the first entry fails the load, rather than loading that entry only");
        Assertions.assertInstanceOf(IOException.class, ex.getCause(), "the io failure is kept as the cause");
    }

    @Test
    public void entriesWithoutLabelAreRejected() {
        final var src = "-----BEGIN -----\nMIID\n-----END -----\n";
        Assertions.assertThrows(PemException.class, () -> Pem.certificate(is(src)), "an entry without label is not a certificate");
        Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(src), null), "an entry without label is not a key");
        Assertions.assertThrows(PemException.class, () -> Pem.keyStore(is(src)), "an entry without label fails the keystore");
    }

    @Test
    public void invalidBase64IsRejected() {
        final var src = "-----BEGIN CERTIFICATE-----\nMIIDA\n-----END CERTIFICATE-----\n";
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.certificate(is(src)), "base64 of an invalid length is a PemException");
        Assertions.assertInstanceOf(IllegalArgumentException.class, ex.getCause(), "the base64 decoder failure is kept as the cause");
    }

    @Test
    public void malformedPkcs1KeysAreRejected() {
        final var src = "-----BEGIN RSA PRIVATE KEY-----\nMAA=\n-----END RSA PRIVATE KEY-----\n";
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.privateKey(is(src), null), "an empty PKCS#1 sequence is a PemException");
        Assertions.assertInstanceOf(DerException.class, ex.getCause(), "the DER failure is kept as the cause");
    }

    private static class CloseRecordingInputStream extends ByteArrayInputStream {

        private boolean closed;

        CloseRecordingInputStream(String src) {
            super(src.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void aStreamReadToTheEndIsLeftOpen() {
        final var stream = new CloseRecordingInputStream(TestData.CERTIFICATE_X509);
        Pem.certificate(stream);
        Assertions.assertFalse(stream.closed, "the stream belongs to the caller, who closes it, even when it is read to its end");
    }

    @Test
    public void aStreamFailingToParseIsLeftOpen() {
        final var stream = new CloseRecordingInputStream("-----BEGIN CERTIFICATE-----\nnot base64 !\n");
        Assertions.assertThrows(PemException.class, () -> Pem.certificate(stream), "malformed PEM is rejected");
        Assertions.assertFalse(stream.closed, "the stream belongs to the caller, who closes it, also on a parse failure");
    }

    @Test
    public void aKeyStoreLoadLeavesTheStreamOpen() throws Exception {
        final var stream = new CloseRecordingInputStream(TestData.CERTIFICATE_X509);
        Pem.keyStore(stream);
        Assertions.assertFalse(stream.closed, "loading a keystore leaves the stream to the caller, as KeyStore.load does");
    }
}
