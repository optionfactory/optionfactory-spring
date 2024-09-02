package net.optionfactory.spring.upstream.hc5;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;

public class SocketFactories {

    public static SSLConnectionSocketFactory trusting(KeyStore keystore, TrustStrategy strategy, HostnameVerifier verifier) {
        try {
            final SSLContext context = new SSLContextBuilder()
                    .loadTrustMaterial(keystore, strategy)
                    .build();
            return new SSLConnectionSocketFactory(context, verifier);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static SSLConnectionSocketFactory trustAll() {
        try {
            final SSLContext context = new SSLContextBuilder()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            return new SSLConnectionSocketFactory(context, new NoopHostnameVerifier());
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            //never happens.
            throw new IllegalStateException(ex);
        }
    }

    public static SSLConnectionSocketFactory system() {
        return null;
    }

}
