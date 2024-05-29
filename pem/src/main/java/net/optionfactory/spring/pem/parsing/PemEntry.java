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
import net.optionfactory.spring.pem.der.DerTokenizer;
import net.optionfactory.spring.pem.der.DerTokenizer.DerValue;

public record PemEntry(String label, List<Metadata> metadata, String b64) {

    public record Metadata(String k, String v) {

    }

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

    public PrivateKey unmarshalPkcs1PrivateKey() {
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
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }
    }

    public EncryptedPrivateKeyInfo unmarshalEncryptedPkcs8PrivateKey() {
        final var bytes = Base64.getDecoder().decode(b64);
        try {
            return new EncryptedPrivateKeyInfo(bytes);
        } catch (IOException ex) {
            throw new PemException(ex);
        }

    }
}
