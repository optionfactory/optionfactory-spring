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

/// Signs PDF documents with a detached PKCS#7 signature, as [Pkcs7PdfSigner] builds it.
///
/// The signature is added as an incremental update, so the original revision of the document is
/// kept byte for byte and earlier signatures stay valid. pdfbox reserves its default space for the
/// signature (9472 bytes), which a long certificate chain can exceed. A signer holds no state but
/// its key material, and can be shared between threads.
///
/// ```java
/// final var signer = new PdfSigner(key, chain);
/// final TemporaryFileSystemResource signed = signer.sign(renderer.render("invoice", context), new SignatureInfo(
///         "ACME", "Invoice", "Milano", ZonedDateTime.now(), SignatureInfo.CommitmentType.PROOF_OF_ORIGIN));
/// ```
public class PdfSigner {

    private final PrivateKey key;
    private final X509Certificate[] certificateChain;

    /// The key is not validated here: an unsupported key fails each signing with the
    /// `IllegalArgumentException` of [Pkcs7PdfSigner].
    ///
    /// @param key the signing key, a RSA key of at least 2048 bits or a 256 bit EC key
    /// @param certificateChain the certificates embedded in the signature, the signer's first
    public PdfSigner(PrivateKey key, X509Certificate[] certificateChain) {
        this.key = key;
        this.certificateChain = certificateChain;
    }

    /// Adds a signature to a loaded document. The signature is computed when the document is
    /// saved with `PDDocument.saveIncremental`, which the caller must do; any other save does not
    /// produce a valid signature.
    ///
    /// @param pdf the document to sign
    /// @param sinfo the signer name, reason, location and time written in the signature dictionary,
    /// and the time and commitment type signed in the PKCS#7 attributes
    /// @throws IllegalArgumentException when the key is not supported
    /// @throws java.io.UncheckedIOException when pdfbox fails to add the signature
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

    /// Signs a document, buffering it in temporary files rather than in memory.
    ///
    /// The document is copied to a temporary file, deleted before returning, and the signed one is
    /// written to another, which the returned resource deletes once read: see
    /// [TemporaryFileSystemResource]. On failure, both files are deleted.
    ///
    /// @param pdf the document to sign; its stream is read once
    /// @param sinfo the signer name, reason, location and time written in the signature dictionary,
    /// and the time and commitment type signed in the PKCS#7 attributes
    /// @return the signed document, readable once
    /// @throws IllegalArgumentException when the key is not supported
    /// @throws java.io.UncheckedIOException when the document cannot be read, parsed or written
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
