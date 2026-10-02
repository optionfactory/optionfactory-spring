package net.optionfactory.spring.pem.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.optionfactory.spring.pem.PemException;
import net.optionfactory.spring.pem.parsing.KeyAndCertificates;
import net.optionfactory.spring.pem.parsing.PemEntry;
import net.optionfactory.spring.pem.parsing.PemParser;

/// The read-only [KeyStoreSpi] behind the `PEM` keystore type, usually obtained through
/// [net.optionfactory.spring.pem.Pem#keyStore(InputStream)].
///
/// [#engineLoad] groups the PEM entries by their `alias` metadata, as documented on
/// [net.optionfactory.spring.pem.Pem#keyStore(InputStream)]: an alias with a key is a key entry
/// whose chain is the alias certificates in file order; an alias with certificates only is trust
/// material, a single certificate kept under the alias and several ones split into `alias.1`,
/// `alias.2`, ... in file order.
///
/// Every mutator, `store` included, throws `UnsupportedOperationException`. A new load replaces
/// the whole content. Reads are safe from multiple threads, also while a load is in progress, but
/// can then observe a partially replaced content.
public class PemKeyStore extends KeyStoreSpi {

    private final Map<String, KeyAndCertificates> data = new ConcurrentHashMap<>();

    /// Replaces the content with the entries of a PEM stream.
    ///
    /// @param stream the PEM stream; `null` empties the keystore, as the [java.security.KeyStore]
    /// contract requires
    /// @param passphrase ignored: encrypted keys are decrypted with the password given to
    /// [#engineGetKey(String, char\[\])]
    /// @throws net.optionfactory.spring.pem.PemException when an entry is malformed or unsupported,
    /// or two keys share an alias; the previous content is then kept
    @Override
    public void engineLoad(InputStream stream, char[] passphrase) throws IOException, NoSuchAlgorithmException, CertificateException {
        if (stream == null) {
            data.clear();
            return;
        }
        final java.util.Map<java.lang.String, net.optionfactory.spring.pem.parsing.KeyAndCertificates> unrolled = PemParser.parse(stream)
                .stream()
                .map(PemEntry::unmarshal)
                .collect(Collectors.toMap(KeyAndCertificates::alias, kac -> kac, (a, b) -> {
                    PemException.ensure((a.key() == null) || (b.key() == null), "Found two keys with the same alias: %s", a.alias());
                    final net.optionfactory.spring.pem.parsing.PrivateKeyHolder key = a.key() != null ? a.key() : b.key();
                    final var certs = Arrays.copyOf(a.certs(), a.certs().length + b.certs().length);
                    System.arraycopy(b.certs(), 0, certs, a.certs().length, b.certs().length);
                    return new KeyAndCertificates(a.alias(), key, certs);
                }))
                .values()
                .stream()
                .<KeyAndCertificates>mapMulti((kac, consumer) -> {
                    if (kac.key() != null || kac.certs().length == 1) {
                        consumer.accept(kac);
                        return;
                    }
                    final var certs = kac.certs();
                    for (int i = 0; i != certs.length; ++i) {
                        final var alias = String.format("%s.%s", kac.alias(), i + 1);
                        final var cur = kac.certs()[i];
                        consumer.accept(new KeyAndCertificates(alias, null, new X509Certificate[]{
                            cur
                        }));
                    }
                }).collect(Collectors.toMap(KeyAndCertificates::alias, kac -> kac));
        data.clear();
        data.putAll(unrolled);
    }

    private Optional<KeyAndCertificates> byAlias(String alias) {
        return Optional.ofNullable(data.get(alias));
    }

    /// @param alias the alias
    /// @param password the passphrase of an encrypted key, ignored for a cleartext one
    /// @return the private key, `null` when the alias is unknown or holds certificates only
    /// @throws net.optionfactory.spring.pem.PemException when an encrypted key cannot be decrypted
    /// with the password
    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return byAlias(alias)
                .map(KeyAndCertificates::key)
                .map(kh -> kh.decrypt(password))
                .orElse(null);
    }

    /// @param alias the alias
    /// @return the certificates of a key entry in file order, empty for a key without certificates;
    /// `null` when the alias is unknown or is a trusted certificate entry, as the [KeyStoreSpi]
    /// contract requires
    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return byAlias(alias)
                .filter(kac -> kac.key() != null)
                .map(KeyAndCertificates::certs)
                .orElse(null);
    }

    /// @param alias the alias
    /// @return the first certificate of the alias, `null` when the alias is unknown or holds a key
    /// without certificates
    @Override
    public Certificate engineGetCertificate(String alias) {
        return byAlias(alias)
                .map(KeyAndCertificates::certs)
                .flatMap(cs -> cs.length == 0 ? Optional.empty() : Optional.of(cs[0]))
                .orElse(null);
    }

    /// PEM carries no creation date: the start of the validity of the first certificate stands in
    /// for it.
    ///
    /// @param alias the alias
    /// @return the `notBefore` of the first certificate, `null` when the alias is unknown or holds a
    /// key without certificates
    @Override
    public Date engineGetCreationDate(String alias) {
        return byAlias(alias)
                .map(KeyAndCertificates::certs)
                .flatMap(cs -> cs.length == 0 ? Optional.empty() : Optional.of(cs[0]))
                .map(X509Certificate::getNotBefore)
                .orElse(null);
    }

    /// @return the aliases, in no particular order
    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(data.keySet());
    }

    /// @param alias the alias
    /// @return whether the alias is in the keystore
    @Override
    public boolean engineContainsAlias(String alias) {
        return data.containsKey(alias);
    }

    /// @return the number of aliases, after the splitting of trust material into numbered aliases
    @Override
    public int engineSize() {
        return data.size();
    }

    /// @param alias the alias
    /// @return whether the alias holds a private key
    @Override
    public boolean engineIsKeyEntry(String alias) {
        return byAlias(alias)
                .map(d -> d.key() != null)
                .orElse(false);
    }

    /// @param alias the alias
    /// @return whether the alias holds a certificate and no key
    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return byAlias(alias)
                .map(d -> d.key() == null && d.certs().length != 0)
                .orElse(false);
    }

    /// @param cert the certificate to look for
    /// @return the alias whose first certificate equals `cert`, `null` when there is none; when
    /// several aliases match, an arbitrary one of them
    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        return data.values()
                .stream()
                .filter(d -> d.certs().length > 0)
                .filter(d -> d.certs()[0].equals(cert))
                .map(d -> d.alias())
                .findFirst()
                .orElse(null);
    }

    /// @throws UnsupportedOperationException always: the keystore is read-only
    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    /// @throws UnsupportedOperationException always: the keystore is read-only
    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    /// @throws UnsupportedOperationException always: the keystore is read-only
    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    /// @throws UnsupportedOperationException always: the keystore is read-only
    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    /// @throws UnsupportedOperationException always: the keystore is read-only
    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }
}
