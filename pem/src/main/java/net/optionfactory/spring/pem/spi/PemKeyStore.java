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
import net.optionfactory.spring.pem.parsing.PemEntry;
import net.optionfactory.spring.pem.parsing.PemEntry.KeyAndCertificates;
import net.optionfactory.spring.pem.parsing.PemParser;

public class PemKeyStore extends KeyStoreSpi {

    private final Map<String, KeyAndCertificates> data = new ConcurrentHashMap<>();

    @Override
    public void engineLoad(InputStream stream, char[] passphrase) throws IOException, NoSuchAlgorithmException, CertificateException {
        final var unrolled = PemParser.parse(stream)
                .stream()
                .map(e -> e.unmarshal(passphrase))
                .collect(Collectors.toMap(KeyAndCertificates::alias, kac -> kac, (a, b) -> {
                    PemException.ensure((a.key() == null) || (b.key() == null), "Found two keys with the same alias: %s", a.alias());
                    //assert (a != null) != (b != null)
                    final var key = a.key() != null ? a.key() : b.key();
                    final var certs = Arrays.copyOf(a.certs(), a.certs().length + b.certs().length);
                    System.arraycopy(b.certs(), 0, certs, a.certs().length, b.certs().length);
                    return new PemEntry.KeyAndCertificates(a.alias(), key, certs);
                }))
                .values()
                .stream()
                .<PemEntry.KeyAndCertificates>mapMulti((kac, consumer) -> {
                    if (kac.key() != null || kac.certs().length == 1) {
                        consumer.accept(kac);
                        return;
                    }
                    //certificates only so it's trust material
                    final var certs = kac.certs();
                    for (int i = 0; i != certs.length; ++i) {
                        final var alias = String.format("%s.%s", kac.alias(), i + 1);
                        final var cur = kac.certs()[i];
                        consumer.accept(new PemEntry.KeyAndCertificates(alias, null, new X509Certificate[]{
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

    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return byAlias(alias)
                .map(KeyAndCertificates::key)
                .orElse(null);
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return byAlias(alias)
                .map(KeyAndCertificates::certs)
                .orElse(new X509Certificate[0]);
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return byAlias(alias)
                .map(KeyAndCertificates::certs)
                .flatMap(cs -> cs.length == 0 ? Optional.empty() : Optional.of(cs[0]))
                .orElse(null);
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return byAlias(alias)
                .map(KeyAndCertificates::certs)
                .flatMap(cs -> cs.length == 0 ? Optional.empty() : Optional.of(cs[0]))
                .map(X509Certificate::getNotBefore)
                .orElse(null);
    }

    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(data.keySet());
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return data.containsKey(alias);
    }

    @Override
    public int engineSize() {
        return data.size();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return byAlias(alias)
                .map(d -> d.key() != null)
                .orElse(false);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return byAlias(alias)
                .map(d -> d.key() == null && d.certs().length != 0)
                .orElse(false);
    }

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

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException("PemKeyStore is immutable.");
    }
}
