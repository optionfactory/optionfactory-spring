package net.optionfactory.spring.pem.parsing;

import java.security.PrivateKey;

public class ClearTextPrivateKeyHolder implements PrivateKeyHolder {

    private final PrivateKey key;

    public ClearTextPrivateKeyHolder(PrivateKey key) {
        this.key = key;
    }

    @Override
    public PrivateKey decrypt(char[] passphrase) {
        return key;
    }
}
