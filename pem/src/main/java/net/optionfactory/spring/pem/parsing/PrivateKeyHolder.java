package net.optionfactory.spring.pem.parsing;

import java.security.PrivateKey;

public interface PrivateKeyHolder {

    PrivateKey decrypt(char[] passphrase);
}
