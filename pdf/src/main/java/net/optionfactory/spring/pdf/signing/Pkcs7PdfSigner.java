package net.optionfactory.spring.pdf.signing;

import net.optionfactory.spring.pem.der.DerWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;

public class Pkcs7PdfSigner implements SignatureInterface {

    public static final String OID_SHA256 = "2.16.840.1.101.3.4.2.1";
    public static final String OID_RSA = "1.2.840.113549.1.1.1";
    public static final String OID_PKCS7_DATA = "1.2.840.113549.1.7.1";
    public static final String OID_PKCS7_SIGNED_DATA = "1.2.840.113549.1.7.2";
    public static final String OID_PKCS9_CONTENT_TYPE = "1.2.840.113549.1.9.3";
    public static final String OID_PKCS9_MESSAGE_DIGEST = "1.2.840.113549.1.9.4";
    public static final String OID_PKCS9_SIGNING_TIME = "1.2.840.113549.1.9.5";
    public static final String OID_AA_SIGNING_CERTIFICATE_V2 = "1.2.840.113549.1.9.16.2.47";
    public static final String OID_AA_CMS_ALGORITHM_PROTECT = "1.2.840.113549.1.9.52";

    public static final String OID_AA_ETS_COMMITMENT_TYPE = "1.2.840.113549.1.9.16.2.16";
    public static final String OID_AA_ETS_SIGNER_LOCATION = "1.2.840.113549.1.9.16.2.17";


    private final PrivateKey privateKey;
    private final X509Certificate[] certificateChain;
    private final SignatureInfo signatureInfo;

    public Pkcs7PdfSigner(PrivateKey privateKey, X509Certificate[] certificateChain, SignatureInfo signatureInfo) {
        this.privateKey = privateKey;
        this.certificateChain = certificateChain;
        this.signatureInfo = signatureInfo;
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            //see: RFC 2315 (PKCS#7) RFC 2985 (PKCS#9) and RFC 5652 (CMS)
            final var contentHash = sha256Of(content);
            final var attributesSet = createAuthenticatedAttributes(contentHash);
            final var signatureBytes = signAttributes(attributesSet);
            return createCmsContainer(attributesSet, signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to generate signature", e);
        }
    }

    private byte[] createCmsContainer(byte[] attributesSet, byte[] signatureBytes) throws CertificateEncodingException, IOException {
        final var authenticatedAttributes = Arrays.copyOf(attributesSet, attributesSet.length);
        authenticatedAttributes[0] = (byte) 0xA0; //[0] IMPLICIT SET of Attributes (changed tag 0x31 to 0xA0)

        final var certBytesList = new ArrayList<byte[]>();
        for (X509Certificate cert : certificateChain) {
            certBytesList.add(cert.getEncoded());
        }
        final var certsBytes = certBytesList.toArray(byte[][]::new);

        return DerWriter.seq(
                DerWriter.oid(OID_PKCS7_SIGNED_DATA),
                DerWriter.explicit(0,
                        DerWriter.seq(
                                DerWriter.integer(1),
                                DerWriter.set(// DigestAlgorithms
                                        DerWriter.seq(
                                                DerWriter.oid(OID_SHA256),
                                                DerWriter.nul()
                                        )
                                ),
                                DerWriter.seq( // ContentInfo
                                        DerWriter.oid(OID_PKCS7_DATA),
                                        null // Detached signature (no content)
                                ),
                                DerWriter.implicit( // [0] IMPLICIT Certificates
                                        0,
                                        certsBytes
                                ),
                                DerWriter.set(// SignerInfos
                                        DerWriter.seq(DerWriter.integer(1), // Version 1
                                                DerWriter.seq( // IssuerAndSerialNumber
                                                        certificateChain[0].getIssuerX500Principal().getEncoded(), // Raw encoded principal to avoid parsing DNs
                                                        DerWriter.integer(certificateChain[0].getSerialNumber())
                                                ),
                                                DerWriter.seq( // DigestAlgorithm
                                                        DerWriter.oid(OID_SHA256),
                                                        DerWriter.nul()
                                                ),
                                                authenticatedAttributes, // AuthenticatedAttributes
                                                DerWriter.seq( // DigestEncryptionAlgorithm
                                                        DerWriter.oid(OID_RSA),
                                                        DerWriter.nul()
                                                ),
                                                DerWriter.octetString(signatureBytes) // EncryptedDigest
                                        )
                                )
                        )
                )
        );
    }

    private byte[] signAttributes(byte[] attributesSet) throws GeneralSecurityException {
        //Sign the DER encoding of authenticatedAttributes (SET tag 0x31 + length + contents)
        final var sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(attributesSet);
        return sig.sign();
    }

    private byte[] createAuthenticatedAttributes(byte[] contentHash) throws IOException, GeneralSecurityException {

        final var attrContentType = DerWriter.seq(
                DerWriter.oid(OID_PKCS9_CONTENT_TYPE),
                DerWriter.set(
                        DerWriter.oid(OID_PKCS7_DATA)
                )
        );
        final var attrMessageDigest = DerWriter.seq(
                DerWriter.oid(OID_PKCS9_MESSAGE_DIGEST),
                DerWriter.set(
                        DerWriter.octetString(contentHash)
                )
        );
        final var attrSigningTime = DerWriter.seq(
                DerWriter.oid(OID_PKCS9_SIGNING_TIME),
                DerWriter.set(
                        DerWriter.utcTime(signatureInfo.at().toInstant())
                )
        );

        final var attrCommitmentType = DerWriter.seq(
                DerWriter.oid(OID_AA_ETS_COMMITMENT_TYPE),
                DerWriter.set(
                        DerWriter.seq(
                                DerWriter.oid(signatureInfo.commitmentType().oid)
                        )
                )
        );

        final var attrAlgorithmProtect = DerWriter.seq(
                DerWriter.oid(OID_AA_CMS_ALGORITHM_PROTECT),
                DerWriter.set(
                        DerWriter.seq(
                                DerWriter.seq(
                                        DerWriter.oid(OID_SHA256),
                                        DerWriter.nul() // Parameters: NULL
                                ),
                                DerWriter.explicit(1, DerWriter.seq( // Sequence { digestAlgorithm, signatureAlgorithm, macAlgorithm [1] OPTIONAL }
                                        DerWriter.oid(OID_RSA),
                                        DerWriter.nul()
                                ))
                        )
                )
        );

        final var certHash = MessageDigest.getInstance("SHA-256").digest(certificateChain[0].getEncoded());

        final var attrSigningCertificateV2 = DerWriter.seq(
                DerWriter.oid(OID_AA_SIGNING_CERTIFICATE_V2),
                DerWriter.set(
                        DerWriter.seq(
                                DerWriter.seq(
                                        DerWriter.seq(
                                                DerWriter.octetString(certHash) 
                                                // TODO: IssuerSerial omitted 
                                                // Sequence { GeneralNames (Sequence), SerialNumber (Integer) }
                                        )
                                )
                        )
                )
        );
        // Attributes must be sorted (by binary repr of DER) 
        final byte[][] attributes = {attrContentType, attrMessageDigest, attrSigningTime, attrCommitmentType, attrAlgorithmProtect, attrSigningCertificateV2};
        Arrays.sort(attributes, Arrays::compareUnsigned);
        return DerWriter.set(attributes);
    }

    private byte[] sha256Of(InputStream content) throws IOException, NoSuchAlgorithmException {
        final var md = MessageDigest.getInstance("SHA-256");
        try (final var dis = new DigestInputStream(content, md)) {
            dis.transferTo(OutputStream.nullOutputStream());
        }
        return md.digest();
    }

}
