package net.optionfactory.spring.pem.parsing;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.optionfactory.spring.pem.PemException;

public class EncryptedPrivateKeyHolder implements PrivateKeyHolder {

    private final EncryptedPrivateKeyInfo pki;

    public EncryptedPrivateKeyHolder(EncryptedPrivateKeyInfo pki) {
        this.pki = pki;
    }

    @Override
    public PrivateKey decrypt(char[] passphrase) {
        PemException.ensure(passphrase != null, "trying to use a null passphrase to unmarshal an encrypted PKCS#8 PrivateKey");
        try {
            final var pbeKey = SecretKeyFactory.getInstance(pki.getAlgName()).generateSecret(new PBEKeySpec(passphrase));
            final var cipher = Cipher.getInstance(pki.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, pki.getAlgParameters());
            final var keySpec = pki.getKeySpec(cipher);
            final var kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        } catch (GeneralSecurityException ex) {
            throw new PemException(ex);
        }
    }
}
