package net.optionfactory.spring.pem.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.optionfactory.spring.pem.Pem;
import net.optionfactory.spring.pem.PemException;
import net.optionfactory.spring.pem.der.DerTokenizer;
import net.optionfactory.spring.pem.der.DerTokenizer.DerValue;

public record PemEntry(String label, List<Metadata> metadata, String b64) {

    public record Metadata(String k, String v) {

    }

    public record KeyAndCertificates(String alias, RSAPrivateKey key, X509Certificate[] certs) {

    }

    public KeyAndCertificates unmarshal(char[] passphrase) {
        final var alias = metadata.stream()
                .filter(m -> m.k().equals("alias"))
                .map(m -> m.v())
                .findFirst()
                .orElse(Pem.DEFAULT_ALIAS);

        return switch (label) {
            case "TRUSTED CERTIFICATE", "X509 CERTIFICATE", "CERTIFICATE" ->
                new PemEntry.KeyAndCertificates(alias, null, new X509Certificate[]{this.x509Certificate()});
            case "RSA PRIVATE KEY" ->
                new PemEntry.KeyAndCertificates(alias, this.unmarshalPkcs1PrivateKey(), new X509Certificate[0]);
            case "ENCRYPTED PRIVATE KEY" ->
                new PemEntry.KeyAndCertificates(alias, this.unmarshalEncryptedPkcs8PrivateKey(passphrase), new X509Certificate[0]);
            case "PRIVATE KEY" ->
                new PemEntry.KeyAndCertificates(alias, this.unmarshalPkcs8PrivateKey(), new X509Certificate[0]);
            default ->
                throw new PemException(String.format("unsupported PEM label: %s", label));
        };
    }

    public RSAPrivateKey unmarshalPrivateKey(char[] passphrase) {
        return switch (label) {
            case "RSA PRIVATE KEY" ->
                this.unmarshalPkcs1PrivateKey();
            case "ENCRYPTED PRIVATE KEY" ->
                this.unmarshalEncryptedPkcs8PrivateKey(passphrase);
            case "PRIVATE KEY" ->
                this.unmarshalPkcs8PrivateKey();
            default ->
                throw new PemException(String.format("unsupported PEM label: %s", label));
        };
    }

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

    public RSAPrivateKey unmarshalPkcs8PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            final var kf = KeyFactory.getInstance("RSA");
            final var ks = new PKCS8EncodedKeySpec(bytes);
            return (RSAPrivateKey) kf.generatePrivate(ks);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }

    }

    public RSAPrivateKey unmarshalPkcs1PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            final var ders = new DerTokenizer(bytes);
            final var sequence = ders.ensureNext().ensureTag(DerValue.TAG_SEQUENCE);
            final var version = ders.ensureNext().asBigInteger(bytes);
            final var modulus = ders.ensureNext().asBigInteger(bytes);
            final var publicExp = ders.ensureNext().asBigInteger(bytes);
            final var privateExp = ders.ensureNext().asBigInteger(bytes);
            final var prime1 = ders.ensureNext().asBigInteger(bytes);
            final var prime2 = ders.ensureNext().asBigInteger(bytes);
            final var exp1 = ders.ensureNext().asBigInteger(bytes);
            final var exp2 = ders.ensureNext().asBigInteger(bytes);
            final var crtCoef = ders.ensureNext().asBigInteger(bytes);

            ders.ensureDone();

            final var keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }
    }

    public RSAPrivateKey unmarshalEncryptedPkcs8PrivateKey(char[] passphrase) {
        PemException.ensure(passphrase != null, "trying to use a null passphrase to unmarshal an encrypted PKCS#8 PrivateKey");
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            final var pki = new EncryptedPrivateKeyInfo(bytes);
            final var pbeKey = SecretKeyFactory.getInstance(pki.getAlgName())
                    .generateSecret(new PBEKeySpec(passphrase));
            final var cipher = Cipher.getInstance(pki.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, pki.getAlgParameters());
            final var keySpec = pki.getKeySpec(cipher);
            final var kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(keySpec);
        } catch (GeneralSecurityException | IOException ex) {
            throw new PemException(ex);
        }

    }
}
