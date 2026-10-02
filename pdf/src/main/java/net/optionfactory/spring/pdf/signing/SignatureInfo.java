package net.optionfactory.spring.pdf.signing;

import java.time.ZonedDateTime;

/// What a [PdfSigner] states about a signature.
///
/// @param name the signer name, written in the signature dictionary
/// @param reason the reason for signing, written in the signature dictionary
/// @param location where the document was signed, written in the signature dictionary
/// @param at the signing time, written in the signature dictionary and signed as the PKCS#9
/// signing time attribute, as a `UTCTime` that only covers 1950 to 2049: a time outside that
/// range fails the signing with an `IllegalArgumentException`. It is not checked against the
/// clock, nor against the validity of the certificate. Required
/// @param commitmentType the commitment the signer makes, signed as the ETSI commitment type
/// attribute. Required
public record SignatureInfo(String name, String reason, String location, ZonedDateTime at, CommitmentType commitmentType) {

    /// The commitment types of RFC 5126 (CAdES), stating what the signer commits to by signing.
    public enum CommitmentType {
        /// The signer created, approved and sent the document.
        PROOF_OF_ORIGIN("1.2.840.113549.1.9.16.6.1"),
        /// The signer received the document.
        PROOF_OF_RECEIPT("1.2.840.113549.1.9.16.6.2"),
        /// A trusted service delivered the document to its recipient.
        PROOF_OF_DELIVERY("1.2.840.113549.1.9.16.6.3"),
        /// The entity sent the document, without necessarily having created it.
        PROOF_OF_SENDER("1.2.840.113549.1.9.16.6.4"),
        /// The signer approved the content of the document.
        PROOF_OF_APPROVAL("1.2.840.113549.1.9.16.6.5"),
        /// The signer created the document, without necessarily having approved or sent it.
        PROOF_OF_CREATION("1.2.840.113549.1.9.16.6.6");

        /// The object identifier of the commitment type.
        public final String oid;

        CommitmentType(String oid) {
            this.oid = oid;
        }
    }

}
