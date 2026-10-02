package net.optionfactory.spring.pem.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.EncryptedPrivateKeyInfo;
import net.optionfactory.spring.pem.Pem;
import net.optionfactory.spring.pem.PemException;
import net.optionfactory.spring.pem.der.DerCursor;
import net.optionfactory.spring.pem.der.DerCursor.Tag;

/// A PEM entry as parsed by [PemParser], not yet decoded.
///
/// The `unmarshal` methods decode the entry and fail with a [PemException] when the label does not
/// match what they decode, or when the content is not valid for it; the base64 decoder's
/// `IllegalArgumentException` and, for PKCS#1 keys, the [net.optionfactory.spring.pem.der.DerException]
/// of the DER parser are not wrapped. An entry without label (`null`) fails the label-checking
/// methods with a `NullPointerException`. Only RSA keys are supported.
///
/// @param label the label of the `-----BEGIN` line, `null` when the line has none
/// @param metadata the `key: value` lines preceding the entry, in file order
/// @param b64 the base64 content, its whitespace removed
public record PemEntry(String label, List<Metadata> metadata, String b64) {

    /// A metadata line preceding a PEM entry, such as `alias: signer`.
    ///
    /// A key starts with a letter followed by at least one letter, digit or dot, and is separated
    /// from the value by `:` or `=`.
    ///
    /// @param k the key, lower-cased
    /// @param v the value, trimmed
    public record Metadata(String k, String v) {

    }

    /// Decodes the entry as keystore material, aliased by its `alias` metadata or
    /// [Pem#DEFAULT_ALIAS] when it has none.
    ///
    /// @return a certificate without key for a certificate label, a key without certificates for a
    /// private key label
    /// @throws PemException when the label is not one of the labels [#unmarshalPrivateKey()] and
    /// [#unmarshalX509Certificate()] support, or the content is invalid for the label
    public KeyAndCertificates unmarshal() {
        final var alias = metadata.stream()
                .filter(m -> m.k().equals("alias"))
                .map(m -> m.v())
                .findFirst()
                .orElse(Pem.DEFAULT_ALIAS);

        return switch (label) {
            case "TRUSTED CERTIFICATE", "X509 CERTIFICATE", "CERTIFICATE" ->
                new KeyAndCertificates(alias, null, new X509Certificate[]{this.x509Certificate()});
            case "RSA PRIVATE KEY" ->
                new KeyAndCertificates(alias, new ClearTextPrivateKeyHolder(this.unmarshalPkcs1PrivateKey()), new X509Certificate[0]);
            case "ENCRYPTED PRIVATE KEY" ->
                new KeyAndCertificates(alias, new EncryptedPrivateKeyHolder(this.unmarshalEncryptedPkcs8PrivateKey()), new X509Certificate[0]);
            case "PRIVATE KEY" ->
                new KeyAndCertificates(alias, new ClearTextPrivateKeyHolder(this.unmarshalPkcs8PrivateKey()), new X509Certificate[0]);
            default ->
                throw new PemException(String.format("unsupported PEM label: %s", label));
        };
    }

    /// Decodes a `PRIVATE KEY` (PKCS#8), `ENCRYPTED PRIVATE KEY` (encrypted PKCS#8) or
    /// `RSA PRIVATE KEY` (PKCS#1) entry. The encrypted key is decrypted only when the holder is
    /// asked for it.
    ///
    /// @return the holder of the key
    /// @throws PemException when the label is not a private key label, or the content is invalid
    public PrivateKeyHolder unmarshalPrivateKey() {
        return switch (label) {
            case "RSA PRIVATE KEY" ->
                new ClearTextPrivateKeyHolder(this.unmarshalPkcs1PrivateKey());
            case "ENCRYPTED PRIVATE KEY" ->
                new EncryptedPrivateKeyHolder(this.unmarshalEncryptedPkcs8PrivateKey());
            case "PRIVATE KEY" ->
                new ClearTextPrivateKeyHolder(this.unmarshalPkcs8PrivateKey());
            default ->
                throw new PemException(String.format("unsupported PEM label: %s", label));
        };
    }

    /// Decodes a `CERTIFICATE`, `X509 CERTIFICATE` or `TRUSTED CERTIFICATE` entry.
    ///
    /// @return the certificate
    /// @throws PemException when the label is not a certificate label, or the content is invalid
    public X509Certificate unmarshalX509Certificate() {
        PemException.ensure(Set.of("TRUSTED CERTIFICATE", "X509 CERTIFICATE", "CERTIFICATE").contains(label), "unsupported PEM label: %s", label);
        return x509Certificate();
    }

    private X509Certificate x509Certificate() {
        final var bytes = Base64.getDecoder().decode(b64);
        try (final var is = new ByteArrayInputStream(bytes)) {
            final var cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(is);
        } catch (IOException | CertificateException ex) {
            throw new PemException(ex);
        }
    }

    /// Decodes the content as a cleartext PKCS#8 RSA key, regardless of the label.
    ///
    /// @return the key
    /// @throws PemException when the content is not a PKCS#8 RSA key
    public PrivateKey unmarshalPkcs8PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            final var kf = KeyFactory.getInstance("RSA");
            final var ks = new PKCS8EncodedKeySpec(bytes);
            return kf.generatePrivate(ks);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }

    }

    /// Decodes the content as a cleartext PKCS#1 `RSAPrivateKey`, regardless of the label. Only
    /// two-prime keys are supported: the optional `otherPrimeInfos` must be absent.
    ///
    /// @return the key, with its CRT parameters
    /// @throws PemException when the RSA parameters are rejected by the key factory
    /// @throws net.optionfactory.spring.pem.der.DerException when the content is not a DER encoded
    /// two-prime PKCS#1 key
    public PrivateKey unmarshalPkcs1PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            final var cursor = DerCursor.nested(bytes);
            final var sequence = cursor.next().ensure(Tag.SEQUENCE);
            final var version = cursor.next().integer(bytes);
            final var modulus = cursor.next().integer(bytes);
            final var publicExp = cursor.next().integer(bytes);
            final var privateExp = cursor.next().integer(bytes);
            final var prime1 = cursor.next().integer(bytes);
            final var prime2 = cursor.next().integer(bytes);
            final var exp1 = cursor.next().integer(bytes);
            final var exp2 = cursor.next().integer(bytes);
            final var crtCoef = cursor.next().integer(bytes);

            cursor.eof();

            final var keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }
    }

    /// Decodes the content as an encrypted PKCS#8 key, regardless of the label, without decrypting
    /// it.
    ///
    /// @return the encrypted key
    /// @throws PemException when the content is not an `EncryptedPrivateKeyInfo`
    public EncryptedPrivateKeyInfo unmarshalEncryptedPkcs8PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            return new EncryptedPrivateKeyInfo(bytes);
        } catch (IOException ex) {
            throw new PemException(ex);
        }

    }
}
