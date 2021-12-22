package net.optionfactory.spring.pdf.signing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.GregorianCalendar;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;

public class PdfSigner {

    private final SignatureInterface signer;

    public PdfSigner(PrivateKey key, Certificate[] certificateChain) {
        this.signer = new BouncyCastleSigner(key, certificateChain);
    }

    public Resource sign(Resource pdf, PdfSignatureInfo sinfo) {
        try {
            final var signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(sinfo.name);
            signature.setLocation(sinfo.location);
            signature.setReason(sinfo.reason);
            signature.setSignDate(GregorianCalendar.from(sinfo.at));
            try (final var is = pdf.getInputStream(); final var reloaded = PDDocument.load(is)) {
                reloaded.addSignature(signature, signer);
                final var signed = new FastByteArrayOutputStream(64 * 1024);
                reloaded.saveIncremental(signed);
                return new ByteArrayResource(signed.toByteArrayUnsafe());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }


    private static class BouncyCastleSigner implements SignatureInterface {

        private final JcaCertStore certs;
        private final SignerInfoGenerator signerInfoGenerator;

        public BouncyCastleSigner(PrivateKey privateKey, Certificate[] certificateChain) {
            try {
                this.certs = new JcaCertStore(List.of(certificateChain));
                final var cert = org.bouncycastle.asn1.x509.Certificate.getInstance(certificateChain[0].getEncoded());
                final var sha1Signer = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
                final var digestCalculatorProvider = new JcaDigestCalculatorProviderBuilder().build();
                this.signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider).build(sha1Signer, new X509CertificateHolder(cert));
            } catch (CertificateEncodingException | OperatorCreationException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public byte[] sign(InputStream content) throws IOException {
            try {
                final var contentBytes = StreamUtils.copyToByteArray(content);
                final var gen = new CMSSignedDataGenerator();
                gen.addSignerInfoGenerator(signerInfoGenerator);
                gen.addCertificates(certs);
                return gen.generate(new CMSProcessableByteArray(contentBytes), false).getEncoded();
            } catch (CMSException | IOException e) {
                throw new IOException(e);
            }
        }

    }

}
