package net.optionfactory.spring.pdf.signing;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Pkcs7PdfSignerVerificationTest {

    private static final SignatureInfo SI = new SignatureInfo(
            "Test Name", "Test Reason", "Italy",
            ZonedDateTime.parse("2000-01-02T10:11:12+01:00[Europe/Rome]"),
            SignatureInfo.CommitmentType.PROOF_OF_ORIGIN);

    private static KeyPair rsa() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static KeyPair ec() throws Exception {
        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
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

    private boolean verifies(KeyPair kp, X509Certificate cert) throws Exception {
        var pkcs7 = new Pkcs7PdfSigner(kp.getPrivate(), new X509Certificate[]{cert}, SI);
        byte[] content = "hello pdf signing".getBytes();
        byte[] cms = pkcs7.sign(new ByteArrayInputStream(content));
        CMSSignedData sd = new CMSSignedData(new CMSProcessableByteArray(content), cms);
        SignerInformation si = sd.getSignerInfos().getSigners().iterator().next();
        SignerInformationVerifier v = new JcaSimpleSignerInfoVerifierBuilder().build(cert);
        return si.verify(v);
    }

    @Test
    public void rsaSignatureVerifies() throws Exception {
        var kp = rsa();
        Assertions.assertTrue(verifies(kp, selfSigned(kp, "SHA256withRSA")));
    }

    @Test
    public void ecdsaSignatureVerifies() throws Exception {
        var kp = ec();
        Assertions.assertTrue(verifies(kp, selfSigned(kp, "SHA256withECDSA")));
    }
}
