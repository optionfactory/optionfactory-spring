package net.optionfactory.spring.pem;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import net.optionfactory.spring.pem.parsing.PemEntry;
import net.optionfactory.spring.pem.parsing.PemParser;
import net.optionfactory.spring.pem.spi.PemProvider;

/// Reads PEM encoded private keys, certificates and keystores.
///
/// The input is a sequence of PEM entries, each optionally preceded by `key: value` (or
/// `key = value`) metadata lines; any other text around the entries, such as the explanatory text
/// RFC 7468 tolerates, is a parse failure. The only metadata in use is `alias`, read by
/// [#keyStore(InputStream)]. Only RSA keys are supported.
///
/// Failures, such as a parse error, an unsupported label or an invalid key or certificate, are
/// reported as a [PemException], with a few exceptions: base64 content of an invalid length fails
/// with the `IllegalArgumentException` of the base64 decoder, a malformed PKCS#1 key with a
/// [net.optionfactory.spring.pem.der.DerException], and a `-----BEGIN -----` line without a label
/// with a `NullPointerException`. An `IOException` raised while reading the stream ends the
/// parsing as if the stream had ended there.
///
/// The streams are read as UTF-8 and are not closed: [PemSources] offers the same readers for
/// spring resources, closing the streams it opens.
///
/// ```java
/// try (InputStream is = Files.newInputStream(path)) {
///     final KeyStore ks = Pem.keyStore(is);
///     final PrivateKey key = (PrivateKey) ks.getKey("signer", passphrase);
/// }
/// ```
public class Pem {

    /// The alias of the entries without `alias` metadata.
    public static final String DEFAULT_ALIAS = "default";

    /// Reads a PEM encoded, possibly encrypted, RSA private key.
    ///
    /// Only the first entry of the stream is considered, and it must be one of:
    ///
    /// - `PRIVATE KEY`: a cleartext PKCS#8 key;
    /// - `ENCRYPTED PRIVATE KEY`: a PKCS#8 key encrypted with a password based scheme the installed
    ///   JCE providers support, such as the PBES2 with AES that `openssl pkcs8 -topk8` produces;
    /// - `RSA PRIVATE KEY`: a cleartext PKCS#1 key (the legacy openssl encryption headers of this
    ///   format are not supported).
    ///
    /// @param is the input stream
    /// @param passphrase the passphrase of an encrypted key, ignored for a cleartext one and therefore
    /// nullable in that case
    /// @return the private key
    /// @throws PemException when the stream holds no entry, the first entry is not a supported key,
    /// or an encrypted key is given a null or a wrong passphrase
    public static PrivateKey privateKey(InputStream is, char[] passphrase) {
        return PemParser.parse(is)
                .stream()
                .findFirst()
                .map(PemEntry::unmarshalPrivateKey)
                .map(kh -> kh.decrypt(passphrase))
                .orElseThrow(() -> new PemException("private key not found"));
    }

    /// Reads a PEM encoded X.509 certificate.
    ///
    /// Only the first entry of the stream is considered, and its label must be one of
    /// `CERTIFICATE`, `X509 CERTIFICATE` or `TRUSTED CERTIFICATE`.
    ///
    /// @param is the input stream
    /// @return the certificate
    /// @throws PemException when the stream holds no entry, or the first entry is not a certificate
    public static X509Certificate certificate(InputStream is) {
        return PemParser.parse(is)
                .stream()
                .findFirst()
                .map(PemEntry::unmarshalX509Certificate)
                .orElseThrow(() -> new PemException("certificate not found"));
    }

    /// Loads a read-only [KeyStore] from a stream of PEM entries.
    ///
    /// Entries are grouped by their `alias` metadata, [#DEFAULT_ALIAS] when they have none, and
    /// each group becomes:
    ///
    /// - a key entry, when it holds a private key: its certificates, in file order, are the key's
    ///   certificate chain, so the end-entity certificate must come first;
    /// - a trusted certificate entry, when it holds a single certificate;
    /// - one trusted certificate entry per certificate, aliased `alias.1`, `alias.2`, ... in file
    ///   order, when it holds several certificates and no key.
    ///
    /// An encrypted key is decrypted on access, with the password given to
    /// [KeyStore#getKey(String, char\[\])] as its passphrase; the password is ignored for cleartext
    /// keys. A key with no certificate is a key entry with an empty chain, which
    /// [KeyStore#getKey(String, char\[\])] serves but [KeyStore#getEntry] cannot turn into a
    /// `PrivateKeyEntry`.
    ///
    /// ```text
    /// alias: signer
    /// -----BEGIN PRIVATE KEY-----
    /// ...
    /// -----END PRIVATE KEY-----
    /// alias: signer
    /// -----BEGIN CERTIFICATE-----
    /// ...
    /// -----END CERTIFICATE-----
    /// ```
    ///
    /// @param is the input stream
    /// @return the loaded keystore, of type [PemProvider#TYPE]
    /// @throws PemException when an entry is malformed or unsupported, or when two keys share an
    /// alias
    public static KeyStore keyStore(InputStream is) {
        try {
            final var ks = KeyStore.getInstance(PemProvider.TYPE, new PemProvider());
            ks.load(is, null);
            return ks;
        } catch (GeneralSecurityException | IOException ex) {
            throw new PemException(ex);
        }
    }

}
