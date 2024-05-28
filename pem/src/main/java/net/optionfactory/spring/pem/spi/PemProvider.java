package net.optionfactory.spring.pem.spi;

import java.security.Provider;

public class PemProvider extends Provider {

    public static final String TYPE = "PEM";

    public PemProvider() {
        super("PEM", 1, "PEM KeyStores/TrustStores");
        put("KeyStore.PEM", PemKeyStore.class.getName());
    }

}
