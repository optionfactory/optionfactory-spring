package net.optionfactory.spring.pem;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import org.springframework.core.io.InputStreamSource;

public class PemSources {

    /**
     * @see Pem#privateKey
     * @param iss the input stream source
     * @param passphrase the passphrase, can be null if the key is not encrypted
     * @return the RSA private key
     */
    public static RSAPrivateKey privateKey(InputStreamSource iss, char[] passphrase) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.privateKey(is, passphrase);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

    /**
     * @see Pem#certificate
     * @param iss the input stream source
     * @return the X509 Certificate
     */
    public static X509Certificate certificate(InputStreamSource iss) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.certificate(is);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

    /**
     * @see Pem#keyStore
     * @param iss the input stream source
     * @param passphrase the passphrase, can be null if keys are in cleartext
     * @return the loaded KeyStore
     */
    public static KeyStore keyStore(InputStreamSource iss, char[] passphrase) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.keyStore(is, passphrase);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

}
