package net.optionfactory.spring.pem.spi;

import java.security.Provider;

/// The security provider of the `PEM` [java.security.KeyStore] type, backed by [PemKeyStore].
///
/// This module does not install the provider in the JVM, so `KeyStore.getInstance("PEM")` does not
/// find it unless one of the following is done:
///
/// - nothing, when an instance is passed explicitly, as
///   [net.optionfactory.spring.pem.Pem#keyStore(java.io.InputStream)] does:
///   `KeyStore.getInstance(PemProvider.TYPE, new PemProvider())`;
/// - `Security.addProvider(new PemProvider())` at runtime, after which `KeyStore.getInstance("PEM")`
///   finds it;
/// - a `security.provider.<n>` entry of the JVM security properties (the `java.security` file, or a
///   file given with `-Djava.security.properties`), naming either the provider, `PEM`, or its class,
///   `net.optionfactory.spring.pem.spi.PemProvider`. The JDK looks providers up by name through a
///   `ServiceLoader` of the system class loader, which finds this one thanks to the
///   `META-INF/services/java.security.Provider` registration of the jar; a class name is loaded
///   through the same class loader. Either way, the jar must be visible to the system class loader,
///   as it is on the class path of a plain `java -cp` launch.
///
/// The registration alone installs nothing: it only lets the JDK resolve the `PEM` name of a
/// security property.
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
