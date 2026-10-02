package net.optionfactory.spring.pem.parsing;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.optionfactory.spring.pem.PemException;

/// Holds an encrypted PKCS#8 private key, decrypting it on every [#decrypt(char\[\])] call.
///
/// The encryption scheme is the one named by the key itself, so any password based scheme the
/// installed JCE providers support is accepted; the decrypted key must be an RSA key.
public class EncryptedPrivateKeyHolder implements PrivateKeyHolder {

    private final EncryptedPrivateKeyInfo pki;

    /// @param pki the encrypted key
    public EncryptedPrivateKeyHolder(EncryptedPrivateKeyInfo pki) {
        this.pki = pki;
    }

    /// @param passphrase the passphrase the key was encrypted with
    /// @return the decrypted RSA key
    /// @throws PemException when the passphrase is null or wrong, the scheme is not supported, or
    /// the key is not an RSA key
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
