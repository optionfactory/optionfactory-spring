package net.optionfactory.spring.pem;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import org.springframework.core.io.InputStreamSource;

/// The [Pem] readers for spring [InputStreamSource]s, such as a `Resource` injected from a
/// property.
///
/// Each method opens one stream from the source, reads it and closes it; a failure to open or close
/// it is reported as a [PemException] caused by the `IOException`. spring-core is an optional
/// dependency of this module: it is needed on the classpath only to use this class.
///
/// ```java
/// @Bean
/// public KeyStore trustStore(@Value("${trust.store}") Resource pem) {
///     return PemSources.keyStore(pem);
/// }
/// ```
public class PemSources {

    /// Reads a private key as [Pem#privateKey(InputStream, char\[\])] does.
    ///
    /// @param iss the source of the PEM stream
    /// @param passphrase the passphrase of an encrypted key, ignored and nullable for a cleartext one
    /// @return the private key
    /// @throws PemException when the source cannot be read or holds no supported key
    public static PrivateKey privateKey(InputStreamSource iss, char[] passphrase) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.privateKey(is, passphrase);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

    /// Reads a certificate as [Pem#certificate(InputStream)] does.
    ///
    /// @param iss the source of the PEM stream
    /// @return the certificate
    /// @throws PemException when the source cannot be read or holds no certificate
    public static X509Certificate certificate(InputStreamSource iss) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.certificate(is);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

    /// Loads a keystore as [Pem#keyStore(InputStream)] does.
    ///
    /// @param iss the source of the PEM stream
    /// @return the loaded, read-only keystore
    /// @throws PemException when the source cannot be read, or holds a malformed or unsupported
    /// entry
    public static KeyStore keyStore(InputStreamSource iss) {
        try (final InputStream is = iss.getInputStream()) {
            return Pem.keyStore(is);
        } catch (IOException ex) {
            throw new PemException(ex);
        }
    }

}
