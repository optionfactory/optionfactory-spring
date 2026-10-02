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
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.springframework.util.Assert;

/// The pdfbox [SignatureInterface] producing the detached PKCS#7 (CMS) `SignedData` of a PDF
/// signature, encoded with [DerWriter] rather than with a crypto library such as BouncyCastle.
///
/// The structure follows RFC 2315 (PKCS#7), RFC 2985 (PKCS#9) and RFC 5652 (CMS):
///
/// - the signed content, the byte ranges of the PDF that pdfbox hands over, is digested with
///   SHA-256 and not embedded: a detached signature, matching the `adbe.pkcs7.detached` subfilter
///   [PdfSigner] declares;
/// - every certificate of the chain is embedded, and the signer is identified by issuer and serial
///   number of the first one, which must therefore be the signer certificate; the issuer is copied
///   as the certificate encodes it, so that no distinguished name is re-encoded;
/// - the signed attributes are the content type, the message digest, the signing time and the
///   commitment type of the [SignatureInfo], the CMS algorithm protection (RFC 6211) and the
///   signing certificate v2, binding the SHA-256 of the signer certificate (its optional
///   `IssuerSerial` is omitted). They are sorted by their encoding, as DER requires of a `SET OF`.
///   The signature is computed over their `SET` encoding (tag `0x31`), while the `SignerInfo`
///   carries the same bytes as `[0] IMPLICIT` (tag `0xA0`);
/// - RFC 6211 defines its module with implicit tags, so the signature algorithm in the CMS
///   algorithm protection is a `[1]` tag directly wrapping the algorithm identifier fields, not a
///   `SEQUENCE`;
/// - the signature algorithm is `SHA256withRSA`, identified as `rsaEncryption`, for an RSA key of at
///   least 2048 bits, and `SHA256withECDSA` for a 256 bit EC key.
///
/// The signing time is the one of the [SignatureInfo], not the current time, and no timestamp is
/// requested from a TSA.
///
/// An instance signs for one [SignatureInfo]: [PdfSigner] creates one per signature.
public class Pkcs7PdfSigner implements SignatureInterface {

    /// SHA-256, the digest algorithm.
    public static final String OID_SHA256 = "2.16.840.1.101.3.4.2.1";
    /// `rsaEncryption`, identifying the signature algorithm of RSA keys.
    public static final String OID_RSA = "1.2.840.113549.1.1.1";
    /// `ecdsa-with-SHA256`, identifying the signature algorithm of EC keys.
    public static final String OID_ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2";

    /// The PKCS#7 `data` content type, the type of the signed content.
    public static final String OID_PKCS7_DATA = "1.2.840.113549.1.7.1";
    /// The PKCS#7 `signedData` content type, the type of the produced structure.
    public static final String OID_PKCS7_SIGNED_DATA = "1.2.840.113549.1.7.2";
    /// The PKCS#9 content type attribute.
    public static final String OID_PKCS9_CONTENT_TYPE = "1.2.840.113549.1.9.3";
    /// The PKCS#9 message digest attribute.
    public static final String OID_PKCS9_MESSAGE_DIGEST = "1.2.840.113549.1.9.4";
    /// The PKCS#9 signing time attribute.
    public static final String OID_PKCS9_SIGNING_TIME = "1.2.840.113549.1.9.5";
    /// The signing certificate v2 attribute of RFC 5035.
    public static final String OID_AA_SIGNING_CERTIFICATE_V2 = "1.2.840.113549.1.9.16.2.47";
    /// The CMS algorithm protection attribute of RFC 6211.
    public static final String OID_AA_CMS_ALGORITHM_PROTECT = "1.2.840.113549.1.9.52";

    /// The ETSI commitment type indication attribute.
    public static final String OID_AA_ETS_COMMITMENT_TYPE = "1.2.840.113549.1.9.16.2.16";
    /// The ETSI signer location attribute, not currently signed.
    public static final String OID_AA_ETS_SIGNER_LOCATION = "1.2.840.113549.1.9.16.2.17";

    private final PrivateKey privateKey;
    private final X509Certificate[] certificateChain;
    private final SignatureInfo signatureInfo;
    private final String signatureAlgorithmName;
    private final String signatureAlgorithmOid;

    /// @param privateKey the signing key: RSA of at least 2048 bits, or EC (`EC` or `ECDSA`) with a
    /// 256 bit order such as P-256
    /// @param certificateChain the certificates to embed, the signer's first
    /// @param signatureInfo the signing time and commitment type to sign
    /// @throws IllegalArgumentException when the key is of another algorithm, size or type, or the
    /// chain is null, empty or holds a null certificate
    public Pkcs7PdfSigner(PrivateKey privateKey, X509Certificate[] certificateChain, SignatureInfo signatureInfo) {
        final var algorithm = validate(privateKey, certificateChain);
        this.privateKey = privateKey;
        this.certificateChain = certificateChain;
        this.signatureInfo = signatureInfo;
        this.signatureAlgorithmName = algorithm.name();
        this.signatureAlgorithmOid = algorithm.oid();
    }

    /// @param name the JCA name of the signature algorithm
    /// @param oid the identifier of the signature algorithm in the `SignerInfo`
    record SignatureAlgorithm(String name, String oid) {

    }

    /// Checks the key and the chain as the constructor does, so that [PdfSigner] rejects them when
    /// it is created rather than on each signature.
    ///
    /// @param privateKey the signing key
    /// @param certificateChain the certificates to embed, the signer's first
    /// @return the signature algorithm of the key
    /// @throws IllegalArgumentException when the key is not supported, or the chain is null, empty
    /// or holds a null certificate
    static SignatureAlgorithm validate(PrivateKey privateKey, X509Certificate[] certificateChain) {
        Assert.notNull(privateKey, "the private key must not be null");
        Assert.notEmpty(certificateChain, "the certificate chain must not be empty");
        Assert.noNullElements(certificateChain, "the certificate chain must not hold null certificates");
        return switch (privateKey.getAlgorithm()) {
            case "EC", "ECDSA" -> {
                if (!(privateKey instanceof ECKey ecKey)) {
                    throw new IllegalArgumentException("Private key claims to be EC/ECDSA but does not implement ECKey.");
                }
                Assert.isTrue(ecKey.getParams().getOrder().bitLength() == 256, "only 256 bit ECDSA keys are supported");
                yield new SignatureAlgorithm("SHA256withECDSA", OID_ECDSA_WITH_SHA256);
            }
            case "RSA" -> {
                if (!(privateKey instanceof RSAKey rsaKey)) {
                    throw new IllegalArgumentException("Private key claims to be RSA but does not implement RSAKey.");
                }
                Assert.isTrue(rsaKey.getModulus().bitLength() >= 2048, "RSA key length must be >= 2048 bits");
                yield new SignatureAlgorithm("SHA256withRSA", OID_RSA);
            }
            default ->
                throw new IllegalArgumentException("key must be RSA or ECDSA");
        };
    }

    /// @param content the bytes to sign, read to the end and closed
    /// @return the DER encoded `ContentInfo` of the `SignedData`
    /// @throws IOException when the content cannot be read, or the signature cannot be computed
    /// @throws IllegalArgumentException when the signing time is outside 1950 to 2049, the range of
    /// the `UTCTime` it is signed as
    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
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
        authenticatedAttributes[0] = (byte) 0xA0;

        final var certBytesList = new ArrayList<byte[]>();
        for (X509Certificate cert : certificateChain) {
            certBytesList.add(cert.getEncoded());
        }
        final var certsBytes = certBytesList.toArray(byte[][]::new);

        final var signatureAlgorithm = OID_RSA.equals(signatureAlgorithmOid)
                ? DerWriter.seq(DerWriter.oid(OID_RSA), DerWriter.nul())
                : DerWriter.seq(DerWriter.oid(signatureAlgorithmOid));

        return DerWriter.seq(
                DerWriter.oid(OID_PKCS7_SIGNED_DATA),
                DerWriter.explicit(0,
                        DerWriter.seq(
                                DerWriter.integer(1),
                                DerWriter.set(
                                        DerWriter.seq(
                                                DerWriter.oid(OID_SHA256),
                                                DerWriter.nul()
                                        )
                                ),
                                DerWriter.seq(
                                        DerWriter.oid(OID_PKCS7_DATA),
                                        null
                                ),
                                DerWriter.implicit(
                                        0,
                                        certsBytes
                                ),
                                DerWriter.set(
                                        DerWriter.seq(DerWriter.integer(1),
                                                DerWriter.seq(
                                                        certificateChain[0].getIssuerX500Principal().getEncoded(),
                                                        DerWriter.integer(certificateChain[0].getSerialNumber())
                                                ),
                                                DerWriter.seq(
                                                        DerWriter.oid(OID_SHA256),
                                                        DerWriter.nul()
                                                ),
                                                authenticatedAttributes,
                                                signatureAlgorithm,
                                                DerWriter.octetString(signatureBytes)
                                        )
                                )
                        )
                )
        );
    }

    private byte[] signAttributes(byte[] attributesSet) throws GeneralSecurityException {
        final var sig = Signature.getInstance(signatureAlgorithmName);
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
        final var protectedSignatureAlgorithm = OID_RSA.equals(signatureAlgorithmOid)
                ? DerWriter.implicit(1, DerWriter.oid(OID_RSA), DerWriter.nul())
                : DerWriter.implicit(1, DerWriter.oid(signatureAlgorithmOid));

        final var attrAlgorithmProtect = DerWriter.seq(
                DerWriter.oid(OID_AA_CMS_ALGORITHM_PROTECT),
                DerWriter.set(
                        DerWriter.seq(
                                DerWriter.seq(
                                        DerWriter.oid(OID_SHA256),
                                        DerWriter.nul()
                                ),
                                protectedSignatureAlgorithm
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
                                        )
                                )
                        )
                )
        );
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
