package net.optionfactory.spring.pdf.signing;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPrivateKeySpec;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Pkcs7PdfSignerVerificationTest {

    private static final SignatureInfo SI = new SignatureInfo(
            "Test Name", "Test Reason", "Italy",
            ZonedDateTime.parse("2000-01-02T10:11:12+01:00[Europe/Rome]"),
            SignatureInfo.CommitmentType.PROOF_OF_APPROVAL);

    private static final byte[] CONTENT = "hello pdf signing".getBytes(StandardCharsets.UTF_8);

    private static KeyPair rsa;
    private static X509Certificate rsaCert;
    private static KeyPair ec;
    private static X509Certificate ecCert;

    @BeforeAll
    public static void keys() throws Exception {
        final var rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        rsa = rsaGenerator.generateKeyPair();
        rsaCert = selfSigned(rsa, "SHA256withRSA");
        final var ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        ec = ecGenerator.generateKeyPair();
        ecCert = selfSigned(ec, "SHA256withECDSA");
    }

    private static X509Certificate selfSigned(KeyPair kp, String sigAlg) throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg).build(kp.getPrivate());
        var notBefore = Date.from(Instant.parse("1999-01-01T00:00:00Z"));
        var notAfter = Date.from(Instant.parse("2100-01-01T00:00:00Z"));
        var holder = new JcaX509v3CertificateBuilder(
                new X500Name("CN=Test"), BigInteger.ONE, notBefore, notAfter,
                new X500Name("CN=Test"), kp.getPublic()).build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static SignerInformation signed(KeyPair kp, X509Certificate cert, byte[] content) throws Exception {
        final var pkcs7 = new Pkcs7PdfSigner(kp.getPrivate(), new X509Certificate[]{cert}, SI);
        final byte[] cms = pkcs7.sign(new ByteArrayInputStream(CONTENT));
        final var sd = new CMSSignedData(new CMSProcessableByteArray(content), cms);
        return sd.getSignerInfos().getSigners().iterator().next();
    }

    private boolean verifies(KeyPair kp, X509Certificate cert) throws Exception {
        return signed(kp, cert, CONTENT).verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
    }

    @Test
    public void rsaSignatureVerifies() throws Exception {
        Assertions.assertTrue(verifies(rsa, rsaCert), "an RSA signature verifies against the signer certificate");
    }

    @Test
    public void ecdsaSignatureVerifies() throws Exception {
        Assertions.assertTrue(verifies(ec, ecCert), "an ECDSA signature verifies against the signer certificate");
    }

    @Test
    public void signatureDoesNotVerifyOtherContent() throws Exception {
        final var other = "hello pdf signinG".getBytes(StandardCharsets.UTF_8);
        final var signer = signed(rsa, rsaCert, other);
        Assertions.assertThrows(CMSSignerDigestMismatchException.class, () -> signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(rsaCert)), "the signed digest does not match different content");
    }

    @Test
    public void signedAttributesCarryTheSignatureInfo() throws Exception {
        final var attributes = signed(rsa, rsaCert, CONTENT).getSignedAttributes();

        final var signingTime = Time.getInstance(attributes.get(CMSAttributes.signingTime).getAttrValues().getObjectAt(0)).getDate();
        Assertions.assertEquals(SI.at().toInstant(), signingTime.toInstant(), "the signing time is the one of the signature info, not the current time");

        final var commitment = ASN1Sequence.getInstance(attributes.get(new ASN1ObjectIdentifier(Pkcs7PdfSigner.OID_AA_ETS_COMMITMENT_TYPE)).getAttrValues().getObjectAt(0));
        Assertions.assertEquals(SignatureInfo.CommitmentType.PROOF_OF_APPROVAL.oid, ASN1ObjectIdentifier.getInstance(commitment.getObjectAt(0)).getId(), "the commitment type is the one of the signature info");

        final var digest = ASN1OctetString.getInstance(attributes.get(CMSAttributes.messageDigest).getAttrValues().getObjectAt(0)).getOctets();
        Assertions.assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(CONTENT), digest, "the message digest is the SHA-256 of the content");

        Assertions.assertNotNull(attributes.get(new ASN1ObjectIdentifier(Pkcs7PdfSigner.OID_AA_SIGNING_CERTIFICATE_V2)), "the signer certificate is bound to the signature");
    }

    @Test
    public void theWholeChainIsEmbeddedAndTheFirstCertificateIdentifiesTheSigner() throws Exception {
        final var pkcs7 = new Pkcs7PdfSigner(rsa.getPrivate(), new X509Certificate[]{rsaCert, ecCert}, SI);
        final var sd = new CMSSignedData(new CMSProcessableByteArray(CONTENT), pkcs7.sign(new ByteArrayInputStream(CONTENT)));
        Assertions.assertEquals(2, sd.getCertificates().getMatches(null).size(), "every certificate of the chain is embedded");
        final var signer = sd.getSignerInfos().getSigners().iterator().next();
        Assertions.assertEquals(rsaCert.getSerialNumber(), signer.getSID().getSerialNumber(), "the signer is identified by the first certificate of the chain");
    }

    @Test
    public void theSignatureIsDetached() throws Exception {
        final var pkcs7 = new Pkcs7PdfSigner(ec.getPrivate(), new X509Certificate[]{ecCert}, SI);
        final var sd = new CMSSignedData(pkcs7.sign(new ByteArrayInputStream(CONTENT)));
        Assertions.assertTrue(sd.isDetachedSignature(), "the signed content is not embedded in the signature");
    }

    @Test
    public void rsaKeysShorterThan2048BitsAreRejected() throws Exception {
        final var modulus = BigInteger.ONE.shiftLeft(1023).setBit(0);
        final var shortKey = KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateKeySpec(modulus, BigInteger.valueOf(3)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Pkcs7PdfSigner(shortKey, new X509Certificate[]{rsaCert}, SI), "a 1024 bit RSA key is too short");
    }

    @Test
    public void ecKeysOtherThan256BitsAreRejected() throws Exception {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        final var p384 = generator.generateKeyPair().getPrivate();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Pkcs7PdfSigner(p384, new X509Certificate[]{ecCert}, SI), "only P-256 sized EC keys are supported");
    }

    @Test
    public void otherKeyAlgorithmsAreRejected() throws Exception {
        final var ed25519 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Pkcs7PdfSigner(ed25519, new X509Certificate[]{ecCert}, SI), "only RSA and ECDSA keys are supported");
    }
}
