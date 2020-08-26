package net.optionfactory.spring.upstream;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;

public class UpstreamSocketFactories {

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
