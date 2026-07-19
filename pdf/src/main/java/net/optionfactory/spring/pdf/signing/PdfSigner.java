package net.optionfactory.spring.pdf.signing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.GregorianCalendar;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.springframework.core.io.Resource;

public class PdfSigner {

    private final PrivateKey key;
    private final X509Certificate[] certificateChain;

    public PdfSigner(PrivateKey key, X509Certificate[] certificateChain) {
        this.key = key;
        this.certificateChain = certificateChain;
    }

    public void sign(PDDocument pdf, SignatureInfo sinfo) {
        final var signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName(sinfo.name());
        signature.setLocation(sinfo.location());
        signature.setReason(sinfo.reason());
        signature.setSignDate(GregorianCalendar.from(sinfo.at()));
        try {
            pdf.addSignature(signature, new Pkcs7PdfSigner(key, certificateChain, sinfo));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public TemporaryFileSystemResource sign(Resource pdf, SignatureInfo sinfo) {
        try {
            final var tempInput = Files.createTempFile("pdf-sign-in-", ".pdf");
            try {
                try (final var is = pdf.getInputStream()) {
                    Files.copy(is, tempInput, StandardCopyOption.REPLACE_EXISTING);
                }
                final var tempOutput = new TemporaryFileSystemResource("pdf-sign-out-", ".pdf");
                try {
                    try (final var reloaded = Loader.loadPDF(tempInput.toFile())) {
                        sign(reloaded, sinfo);
                        try (final var fos = Files.newOutputStream(tempOutput.getFile().toPath())) {
                            reloaded.saveIncremental(fos);
                        }
                    }
                    return tempOutput;
                } catch (IOException | RuntimeException ex) {
                    tempOutput.discard();
                    throw ex;
                }
            } finally {
                try {
                    Files.deleteIfExists(tempInput);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
