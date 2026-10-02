package net.optionfactory.spring.pem.spi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.optionfactory.spring.pem.Pem;
import net.optionfactory.spring.pem.PemException;
import net.optionfactory.spring.pem.TestData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PemKeyStoreTest {

    private static InputStream is(String src) {
        return new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> aliases(KeyStore ks) throws Exception {
        return new HashSet<>(Collections.list(ks.aliases()));
    }

    @Test
    public void aliasMetadataGroupsAKeyWithItsCertificates() throws Exception {
        final var src = "alias: signer\n" + TestData.PRIVATE_KEY_PKCS8_CLEARTEXT
                + "alias: signer\n" + TestData.CERTIFICATE_X509;
        final var ks = Pem.keyStore(is(src));
        Assertions.assertEquals(Set.of("signer"), aliases(ks), "entries sharing an alias are merged into one");
        Assertions.assertTrue(ks.isKeyEntry("signer"), "an alias with a key is a key entry");
        Assertions.assertFalse(ks.isCertificateEntry("signer"), "an alias with a key is not a trusted certificate entry");
        Assertions.assertEquals(1, ks.getCertificateChain("signer").length, "the certificates of the alias are the key's chain");
    }

    @Test
    public void metadataKeysAreCaseInsensitiveAndAcceptEitherSeparator() throws Exception {
        final var src = "ALIAS = first\n" + TestData.CERTIFICATE_X509
                + "Alias:second\n" + TestData.CERTIFICATE_X509;
        final var ks = Pem.keyStore(is(src));
        Assertions.assertEquals(Set.of("first", "second"), aliases(ks), "the alias key is matched regardless of case, after ':' or '='");
    }

    @Test
    public void aSingleCertificateKeepsItsAlias() throws Exception {
        final var ks = Pem.keyStore(is("alias: ca\n" + TestData.CERTIFICATE_X509));
        Assertions.assertEquals(Set.of("ca"), aliases(ks), "a lone certificate is not suffixed with an index");
        Assertions.assertTrue(ks.isCertificateEntry("ca"), "a lone certificate is a trusted certificate entry");
        Assertions.assertInstanceOf(KeyStore.TrustedCertificateEntry.class, ks.getEntry("ca", null), "a lone certificate is exposed as trust material");
    }

    @Test
    public void certificatesOnlyAliasesAreNumberedInFileOrder() throws Exception {
        final var ks = Pem.keyStore(is(TestData.CERTIFICATE_X509_CHAIN));
        final var first = (X509Certificate) ks.getCertificate("default.1");
        final var second = (X509Certificate) ks.getCertificate("default.2");
        Assertions.assertTrue(first.getSubjectX500Principal().getName().contains("www.example.org"), "the first certificate in the file is numbered 1");
        Assertions.assertTrue(second.getSubjectX500Principal().getName().contains("DigiCert"), "the second certificate in the file is numbered 2");
        Assertions.assertEquals("default.2", ks.getCertificateAlias(second), "a certificate is found back by its alias");
    }

    @Test
    public void creationDateIsTheCertificateNotBefore() throws Exception {
        final var ks = Pem.keyStore(is(TestData.CERTIFICATE_X509));
        final var cert = (X509Certificate) ks.getCertificate(Pem.DEFAULT_ALIAS);
        Assertions.assertEquals(cert.getNotBefore(), ks.getCreationDate(Pem.DEFAULT_ALIAS), "the creation date is the start of the certificate validity");
    }

    @Test
    public void keyWithoutCertificatesHasNoCreationDate() throws Exception {
        final var ks = Pem.keyStore(is(TestData.PRIVATE_KEY_PKCS1));
        Assertions.assertNull(ks.getCreationDate(Pem.DEFAULT_ALIAS), "without certificates there is no date to report");
        Assertions.assertNull(ks.getCertificate(Pem.DEFAULT_ALIAS), "a key without certificates has no certificate");
    }

    @Test
    public void encryptedKeysAreDecryptedWithTheKeyPassword() throws Exception {
        final var ks = Pem.keyStore(is(TestData.PRIVATE_KEY_PKCS8_ENCRYPTED));
        Assertions.assertEquals("RSA", ks.getKey(Pem.DEFAULT_ALIAS, "changeit".toCharArray()).getAlgorithm(), "the key password is the PEM passphrase");
        Assertions.assertThrows(PemException.class, () -> ks.getKey(Pem.DEFAULT_ALIAS, "wrong".toCharArray()), "a wrong key password fails the decryption");
    }

    @Test
    public void unknownAliasesHaveNoKeyNorCertificate() throws Exception {
        final var ks = Pem.keyStore(is(TestData.CERTIFICATE_X509));
        Assertions.assertFalse(ks.containsAlias("missing"), "an alias not in the file is not contained");
        Assertions.assertNull(ks.getKey("missing", null), "an unknown alias has no key");
        Assertions.assertNull(ks.getCertificate("missing"), "an unknown alias has no certificate");
        Assertions.assertNull(ks.getKey(Pem.DEFAULT_ALIAS, null), "a certificate alias has no key");
    }

    @Test
    public void twoKeysWithTheSameAliasAreRejected() {
        final var src = TestData.PRIVATE_KEY_PKCS1 + TestData.PRIVATE_KEY_PKCS8_CLEARTEXT;
        final var ex = Assertions.assertThrows(PemException.class, () -> Pem.keyStore(is(src)), "an alias can hold one key only");
        Assertions.assertTrue(ex.getMessage().contains("default"), "the failure names the contended alias");
    }

    @Test
    public void unsupportedEntriesFailTheLoad() {
        Assertions.assertThrows(PemException.class, () -> Pem.keyStore(is(TestData.PRIVATE_KEY_EC)), "an entry with an unsupported label fails the whole keystore");
    }

    @Test
    public void reloadingReplacesTheContent() throws Exception {
        final var ks = Pem.keyStore(is(TestData.CERTIFICATE_X509_CHAIN));
        ks.load(is(TestData.CERTIFICATE_X509), null);
        Assertions.assertEquals(Set.of(Pem.DEFAULT_ALIAS), aliases(ks), "a load discards the entries of the previous one");
    }

    @Test
    public void theKeyStoreIsReadOnly() throws Exception {
        final var ks = Pem.keyStore(is(TestData.CERTIFICATE_X509));
        final var cert = ks.getCertificate(Pem.DEFAULT_ALIAS);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> ks.setCertificateEntry("other", cert), "entries cannot be added");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> ks.deleteEntry(Pem.DEFAULT_ALIAS), "entries cannot be removed");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> ks.store(java.io.OutputStream.nullOutputStream(), null), "the keystore cannot be stored");
    }

    @Test
    public void theProviderRegistersThePemKeyStoreType() throws Exception {
        final var ks = KeyStore.getInstance(PemProvider.TYPE, new PemProvider());
        ks.load(is(TestData.CERTIFICATE_X509), null);
        Assertions.assertEquals(1, ks.size(), "the provider instantiates a working PEM keystore");
    }
}
