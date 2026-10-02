package net.optionfactory.spring.pem.parsing;

import java.security.PrivateKey;

/// A private key read from a PEM entry, whose decryption is deferred until the passphrase is
/// known: a keystore is loaded without passwords, and receives one only when a key is requested.
public interface PrivateKeyHolder {

    /// @param passphrase the passphrase of an encrypted key; implementations holding a cleartext key
    /// ignore it
    /// @return the private key
    /// @throws net.optionfactory.spring.pem.PemException when the key cannot be decrypted with the
    /// passphrase
    PrivateKey decrypt(char[] passphrase);
}
