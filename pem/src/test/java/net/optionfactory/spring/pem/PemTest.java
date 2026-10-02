package net.optionfactory.spring.pem;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PemTest {

    private InputStream is(String src) {
        return new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void canLoadKeyStoreWithKey() throws GeneralSecurityException {

        final var src = TestData.PRIVATE_KEY_PKCS8_CLEARTEXT;
        final var ks = Pem.keyStore(is(src));
        final var key = ks.getKey(Pem.DEFAULT_ALIAS, "unused".toCharArray());
        Assertions.assertEquals("RSA", key.getAlgorithm(), "the key under the default alias is an RSA key");
        Assertions.assertEquals("PKCS#8", key.getFormat(), "the key is exposed in PKCS#8 encoding");
        final var rsa = (RSAPrivateKey) key;
        Assertions.assertEquals(new BigInteger("18602129590178376040338768239167770085824924297180461905478851733756483031116343728192238080232366056577957402196904785177277201500754079560010273530610920138446680224652458504414451335021391974866981183601129222611076623243956748464131335582064140880049680159709846947197762329845666219102681189297086105047437137762325088742829302034844284976555820341881867112660323084425405915126022985787049455249655739930231587762252843464011433242585047448285230810146397656476104262040061054266849081822984414682968732970351382583266443873909921458658306538062954444191006749173799356669776375507144318676426944343183830594847"), rsa.getModulus(), "the modulus is the one encoded in the PEM");
        Assertions.assertEquals(new BigInteger("4701978694876175111711428128628617351369955650166210563425392044120771772463765384887445563850790544428981389842269652545191044470463123196840718772164352923591840964058170214609725705941634019861436696781123733725434331787353182426454791027735226295809435535433014474324496572547696473023566309738024015032100894933958403151586784761453048072203557850963254483156544249561882528060111969166149408342057319856671748738145853838332924327973696795511966688121721264322403498181419139383389101756343763875302001109844319977272125624848711757351069053936240318062188673149018754478327690768556119920489810629602707220413"), rsa.getPrivateExponent(), "the private exponent is the one encoded in the PEM");
    }

    @Test
    public void canLoadKeyStoreWithKeyAndCertificate() throws GeneralSecurityException {
        final var src = TestData.PRIVATE_KEY_PKCS8_CLEARTEXT_AND_CERTIFICATE_CHAIN;
        final var ks = Pem.keyStore(is(src));

        final var key = ks.getKey(Pem.DEFAULT_ALIAS, "unused".toCharArray());

        Assertions.assertEquals(Set.of("default"), new HashSet<>(Collections.list(ks.aliases())), "a key and its certificate without alias metadata share the default alias");

        final var entry = ks.getEntry(Pem.DEFAULT_ALIAS, new KeyStore.PasswordProtection("unused".toCharArray()));
        
        Assertions.assertEquals(KeyStore.PrivateKeyEntry.class, entry.getClass(), "an alias with a key is a private key entry");
        Assertions.assertEquals(1, ((KeyStore.PrivateKeyEntry) entry).getCertificateChain().length, "the certificate is the key's chain");
    }

    @Test
    public void canLoadTrustedCertificates() throws GeneralSecurityException {
        final var src = TestData.CERTIFICATE_X509_CHAIN;
        final var ks = Pem.keyStore(is(src));

        Assertions.assertEquals(Set.of("default.1", "default.2"), new HashSet<>(Collections.list(ks.aliases())), "certificates without a key are numbered under their alias");
        Assertions.assertEquals(KeyStore.TrustedCertificateEntry.class, ks.getEntry("default.1", null).getClass(), "the first certificate is trust material");
        Assertions.assertEquals(KeyStore.TrustedCertificateEntry.class, ks.getEntry("default.2", null).getClass(), "the second certificate is trust material");
    }

}
