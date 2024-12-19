package net.optionfactory.spring.upstream.hc5;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.lang.Nullable;

public class HcSocketStrategies {

    public static TlsSocketStrategy trusting(KeyStore keystore, TrustStrategy strategy, HostnameVerificationPolicy policy, HostnameVerifier verifier) {
        try {
            final SSLContext context = new SSLContextBuilder()
                    .loadTrustMaterial(keystore, strategy)
                    .build();
            return new DefaultClientTlsStrategy(context, policy, verifier);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static TlsSocketStrategy trustAll() {
        try {
            final SSLContext context = new SSLContextBuilder()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            return new DefaultClientTlsStrategy(context, HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            //never happens.
            throw new IllegalStateException(ex);
        }
    }

    public static TlsSocketStrategy system() {
        return DefaultClientTlsStrategy.createSystemDefault();
    }

    public static TlsSocketStrategy defaults() {
        return DefaultClientTlsStrategy.createDefault();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final SSLContextBuilder inner = new SSLContextBuilder();
        private String[] protocols;
        private String[] cipherSuites;
        private SSLBufferMode sslBufferMode;
        private HostnameVerificationPolicy hostnameVerificationPolicy;
        private HostnameVerifier hostnameVerifier;

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

        /**
         * defaults to HostnameVerificationPolicy.BOTH
         *
         * @param hostnameVerificationPolicy
         * @return
         */
        public Builder hostnameVerificationPolicy(@Nullable HostnameVerificationPolicy hostnameVerificationPolicy) {
            this.hostnameVerificationPolicy = hostnameVerificationPolicy;
            return this;
        }

        /**
         *
         * defaults to NoopHostnameVerifier.INSTANCE if
         * HostnameVerificationPolicy.BUILTIN {
         *
         * @see HttpsSupport.getDefaultHostnameVerifier()} otherwise.
         *
         * @param hostnameVerifier
         * @return
         */
        public Builder hostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * defaults to SSLBufferMode.STATIC
         *
         * @param sslBufferMode
         * @return
         */
        public Builder sslBufferMode(SSLBufferMode sslBufferMode) {
            this.sslBufferMode = sslBufferMode;
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

        public TlsSocketStrategy build() {
            try {
                return new DefaultClientTlsStrategy(inner.build(), protocols, cipherSuites, sslBufferMode, hostnameVerificationPolicy, hostnameVerifier);
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
