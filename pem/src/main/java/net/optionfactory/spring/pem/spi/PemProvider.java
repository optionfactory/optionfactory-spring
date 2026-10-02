package net.optionfactory.spring.pem.spi;

import java.security.Provider;

/// The security provider of the `PEM` [java.security.KeyStore] type, backed by [PemKeyStore].
///
/// The provider is not installed in the JVM by this module, so `KeyStore.getInstance("PEM")` does
/// not find it: pass an instance explicitly, or use
/// [net.optionfactory.spring.pem.Pem#keyStore(java.io.InputStream)].
///
/// ```java
/// final KeyStore ks = KeyStore.getInstance(PemProvider.TYPE, new PemProvider());
/// ks.load(pemStream, null);
/// ```
public class PemProvider extends Provider {

    /// The keystore type this provider registers.
    public static final String TYPE = "PEM";

    /// Creates the provider, named `PEM`, registering [#TYPE].
    public PemProvider() {
        super("PEM", "1", "PEM KeyStores/TrustStores");
        put("KeyStore.PEM", PemKeyStore.class.getName());
    }

}
