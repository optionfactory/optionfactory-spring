package net.optionfactory.spring.pem;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import net.optionfactory.spring.pem.parsing.PemParser;
import net.optionfactory.spring.pem.spi.PemProvider;
import org.springframework.core.io.InputStreamSource;

public class Pem {

    public static final String DEFAULT_ALIAS = "default";

    /**
     * Reads a PEM encoded (possibly encrypted) private key. Supported formats
     * are:
     * <ul>
     * <li>PKCS8 cleartext: PEM label must be "PRIVATE KEY"
     * <li>PKCS8 encrypted: PEM label must be "ENCRYPTED PRIVATE KEY". Only aes
     * encryption is supported.
     * <li>PKCS1 cleartext: PEM label must be "RSA PRIVATE KEY"
     * </ul>
     *
     * @param is the input stream
     * @param passphrase the passphrase, can be null if the key is not encrypted
     * @return the RSA private key
     */
    public static RSAPrivateKey privateKey(InputStream is, char[] passphrase) {
        return PemParser.parse(is)
                .stream()
                .findFirst()
                .map(e -> e.unmarshalPrivateKey(passphrase))
                .orElseThrow(() -> new PemException("private key not found"));
    }

    /**
     * Reads a PEM encoded X.509 certificated. PEM label must be one of:
     * <ul>
     * <li>"TRUSTED CERTIFICATE"
     * <li>"X509 CERTIFICATE"
     * <li>"CERTIFICATE"
     * </ul>
     *
     * @param is the input stream
     * @return the X509 Certificate
     */
    public static X509Certificate certificate(InputStream is) {
        return PemParser.parse(is)
                .stream()
                .findFirst()
                .map(e -> e.unmarshalX509Certificate())
                .orElseThrow(() -> new PemException("certificate not found"));
    }

    /**
     * Loads a keystore from a PEM InputStream
     * <ul>
     * <li>supports aliases via prefixed metadata
     * <li>default alias is "default"
     * <li>when only certificates are associated to an alias they are normalized
     * to a list of trusted certificates with alias `alias.index`
     * <li>when a private key and certificates are associated to an alias,
     * certificates are assumed to be the private key certificate chain.
     * </ul>
     *
     * @param is the input stream
     * @return the loaded KeyStore
     */
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
