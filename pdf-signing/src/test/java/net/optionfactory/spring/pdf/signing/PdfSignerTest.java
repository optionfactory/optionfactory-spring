package net.optionfactory.spring.pdf.signing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public class PdfSignerTest {

    private PdfSigner signer;

    @BeforeEach
    public void setup() throws Exception {
        // keytool -genkeypair -storepass "changeit" -storetype pkcs12 -alias pdf -validity 3650 -v -keyalg RSA -keystore devpdf.pkcs12
        try (InputStream is = PdfSignerTest.class.getResourceAsStream("/example/teststore.pkcs12")) {
            final KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(is, "changeit".toCharArray());
            final PrivateKey privateKey = (PrivateKey) keystore.getKey("pdf", "changeit".toCharArray());
            final Certificate[] certificateChain = keystore.getCertificateChain("pdf");
            this.signer = new PdfSigner(privateKey, certificateChain);
        }
    }

    @Test
    public void canSign() throws Exception {
        final PdfSignatureInfo si = new PdfSignatureInfo();
        si.name = "Test Name";
        si.reason = "Test Reason";
        si.at = ZonedDateTime.now(ZoneId.of("Europe/Rome"));
        si.location = "Italy";
        Resource signed = signer.sign(new ClassPathResource("/example/example.pdf"), si);
        dump(signed, "target/signed.pdf");

        final var signedFile = new File("target/signed.pdf");
        try (PDDocument document = Loader.loadPDF(signedFile)) {
            final var signatureDictionaries = document.getSignatureDictionaries();
            final var pdSignature = signatureDictionaries.get(0);
            final var signatureContent = pdSignature.getContents(new FileInputStream(signedFile));
            final var signedContent = pdSignature.getSignedContent(new FileInputStream(signedFile));
            final var cmsProcessableInputStream = new CMSProcessableByteArray(signedContent);
            final var cmsSignedData = new CMSSignedData(cmsProcessableInputStream, signatureContent);
            final var signerInformationStore = cmsSignedData.getSignerInfos();
            final var signers = signerInformationStore.getSigners();
            final var signer = signers.stream().findFirst().orElseThrow();
            Assertions.assertNotNull(signer);
            Assertions.assertEquals(si.name, pdSignature.getName());
            Assertions.assertEquals(si.reason, pdSignature.getReason());
            Assertions.assertEquals(si.location, pdSignature.getLocation());
        }

    }

    public void dump(Resource in, String out) {
        try (var fos = new FileOutputStream(out); var is = in.getInputStream()) {
            StreamUtils.copy(is, fos);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

    }
}
