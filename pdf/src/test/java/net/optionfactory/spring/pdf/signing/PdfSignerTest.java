package net.optionfactory.spring.pdf.signing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class PdfSignerTest {

    private static final SignatureInfo SI = new SignatureInfo(
            "Test Name",
            "Test Reason",
            "Italy",
            ZonedDateTime.parse("2000-01-02T10:11:12+01:00[Europe/Rome]"),
            SignatureInfo.CommitmentType.PROOF_OF_ORIGIN
    );

    private static PdfSigner signer;
    private static X509Certificate certificate;

    @TempDir
    Path dir;

    /// The keystore was generated with
    /// `keytool -genkeypair -storepass "changeit" -storetype pkcs12 -alias pdf -validity 3650 -v -keyalg RSA -keystore devpdf.pkcs12`.
    @BeforeAll
    public static void setup() throws Exception {
        try (InputStream is = PdfSignerTest.class.getResourceAsStream("/example/teststore.pkcs12")) {
            final KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(is, "changeit".toCharArray());
            final var privateKey = (PrivateKey) keystore.getKey("pdf", "changeit".toCharArray());
            final var cert = keystore.getCertificateChain("pdf");
            final var x509Chain = Arrays.copyOf(
                    cert,
                    cert.length,
                    X509Certificate[].class
            );
            certificate = x509Chain[0];
            signer = new PdfSigner(privateKey, x509Chain);
        }
    }

    private Path signed() throws Exception {
        final var target = dir.resolve("signed.pdf");
        final Resource signed = signer.sign(new ClassPathResource("/example/example.pdf"), SI);
        try (final var is = signed.getInputStream()) {
            Files.copy(is, target);
        }
        return target;
    }

    @Test
    public void canSign() throws Exception {
        final var signedFile = signed();
        try (PDDocument document = Loader.loadPDF(signedFile.toFile())) {
            final var pdSignature = document.getSignatureDictionaries().get(0);
            Assertions.assertEquals(SI.name(), pdSignature.getName(), "the signature dictionary carries the signer name");
            Assertions.assertEquals(SI.reason(), pdSignature.getReason(), "the signature dictionary carries the reason");
            Assertions.assertEquals(SI.location(), pdSignature.getLocation(), "the signature dictionary carries the location");
            Assertions.assertEquals(SI.at().toInstant(), pdSignature.getSignDate().toInstant(), "the signature dictionary carries the signing time");
        }
    }

    @Test
    public void theSignatureVerifiesAgainstTheSignedRanges() throws Exception {
        final var signedFile = signed();
        final var bytes = Files.readAllBytes(signedFile);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            final var pdSignature = document.getSignatureDictionaries().get(0);
            final var signatureContent = pdSignature.getContents(new ByteArrayInputStream(bytes));
            final var signedContent = pdSignature.getSignedContent(new ByteArrayInputStream(bytes));
            final var cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), new ByteArrayInputStream(signatureContent));
            final var signerInfo = cms.getSignerInfos().getSigners().iterator().next();
            Assertions.assertTrue(signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate.getPublicKey())), "the CMS signature verifies against the byte ranges it covers");
            Assertions.assertEquals(bytes.length, pdSignature.getByteRange()[2] + pdSignature.getByteRange()[3], "the signature covers the whole file but its own content");
        }
    }

    @Test
    public void theOriginalRevisionIsKept() throws Exception {
        final var original = new ClassPathResource("/example/example.pdf").getContentAsByteArray();
        final var signed = Files.readAllBytes(signed());
        Assertions.assertArrayEquals(original, Arrays.copyOf(signed, original.length), "the signature is appended as an incremental update");
    }

}
