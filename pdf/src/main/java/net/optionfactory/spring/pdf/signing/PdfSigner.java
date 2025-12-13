package net.optionfactory.spring.pdf.signing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.GregorianCalendar;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FastByteArrayOutputStream;

public class PdfSigner {

    private final PrivateKey key;
    private final X509Certificate[] certificateChain;

    public PdfSigner(PrivateKey key, X509Certificate[] certificateChain) {
        this.key = key;
        this.certificateChain = certificateChain;
    }

    public Resource sign(Resource pdf, SignatureInfo sinfo) {
        try {
            final var signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(sinfo.name());
            signature.setLocation(sinfo.location());
            signature.setReason(sinfo.reason());
            signature.setSignDate(GregorianCalendar.from(sinfo.at()));
            final var bytes = pdf.getContentAsByteArray();

            
            try (final var reloaded = Loader.loadPDF(bytes)) {
                reloaded.addSignature(signature, new Pkcs7PdfSigner(key, certificateChain, sinfo));
                final var signed = new FastByteArrayOutputStream(64 * 1024);
                reloaded.saveIncremental(signed);
                return new ByteArrayResource(signed.toByteArrayUnsafe());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
