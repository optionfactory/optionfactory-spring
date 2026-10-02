package net.optionfactory.spring.pem.parsing;

import java.security.PrivateKey;

/// Holds a private key that was not encrypted in its PEM entry.
public class ClearTextPrivateKeyHolder implements PrivateKeyHolder {

    private final PrivateKey key;

    /// @param key the key to hold
    public ClearTextPrivateKeyHolder(PrivateKey key) {
        this.key = key;
    }

    /// @param passphrase ignored, and therefore nullable
    /// @return the held key
    @Override
    public PrivateKey decrypt(char[] passphrase) {
        return key;
    }
}
