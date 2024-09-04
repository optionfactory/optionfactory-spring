package net.optionfactory.spring.upstream.hc5;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.lang.Nullable;

public class HcSocketFactories {

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final SSLContextBuilder inner = new SSLContextBuilder();
        private HostnameVerifier hostnameVerifier;
        private String[] protocols;
        private String[] cipherSuites;

        public Builder key(KeyStore keyStore, char[] keySecret) {
            try {
                inner.loadKeyMaterial(keyStore, keySecret);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public Builder key(KeyStore keyStore, char[] keySecret, PrivateKeyStrategy aliasStrategy) {
            try {
                inner.loadKeyMaterial(keyStore, keySecret, aliasStrategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public Builder trust(@Nullable TrustStrategy strategy) {
            try {
                inner.loadTrustMaterial(strategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public Builder trust(@Nullable KeyStore keyStore, @Nullable TrustStrategy strategy) {
            try {
                inner.loadTrustMaterial(keyStore, strategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public Builder hostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        public Builder protocols(String... protocols) {
            this.protocols = protocols;
            return this;
        }

        public Builder cipherSuites(String... cipherSuites) {
            this.cipherSuites = cipherSuites;
            return this;
        }

        public SSLConnectionSocketFactory build() {
            try {
                return new SSLConnectionSocketFactory(inner.build(), protocols, cipherSuites, hostnameVerifier);
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
