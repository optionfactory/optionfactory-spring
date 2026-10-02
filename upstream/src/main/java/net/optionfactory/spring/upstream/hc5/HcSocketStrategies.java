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
import org.jspecify.annotations.Nullable;

/// Creates the TLS strategies of the HttpComponents clients, see
/// [HcRequestFactories.Builder#tlsSocketStrategy(Function)].
public class HcSocketStrategies {

    /// Trusts the certificates of a trust store, plus those the strategy accepts.
    ///
    /// @param keystore the trust store, or `null` for the JDK default one
    /// @param strategy accepts certificates the trust store would reject, or `null`
    /// @param policy who verifies the hostname: JSSE, the verifier or both
    /// @param verifier the hostname verifier
    /// @return the strategy
    /// @throws IllegalStateException when the trust store cannot be loaded
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

    /// Trusts every certificate and skips hostname verification: never use it against a production
    /// upstream, as it defeats TLS authentication.
    ///
    /// @return the strategy
    public static TlsSocketStrategy trustAll() {
        try {
            final SSLContext context = new SSLContextBuilder()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            return new DefaultClientTlsStrategy(context, HostnameVerificationPolicy.CLIENT, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// @return the strategy configured by the JSSE system properties (`javax.net.ssl.*`,
    /// `https.protocols`, `https.cipherSuites`)
    public static TlsSocketStrategy system() {
        return DefaultClientTlsStrategy.createSystemDefault();
    }

    /// @return the HttpComponents default strategy: the default `SSLContext` with JSSE hostname
    /// verification, ignoring system properties
    public static TlsSocketStrategy defaults() {
        return DefaultClientTlsStrategy.createDefault();
    }

    /// @return a builder of a custom strategy
    public static Builder builder() {
        return new Builder();
    }

    /// Builds a custom TLS strategy, e.g. one presenting a client certificate.
    ///
    /// Without trust material, the JDK default trust store is used.
    public static class Builder {

        private final SSLContextBuilder inner = new SSLContextBuilder();
        private String[] protocols;
        private String[] cipherSuites;
        private SSLBufferMode sslBufferMode;
        private HostnameVerificationPolicy hostnameVerificationPolicy;
        private HostnameVerifier hostnameVerifier;

        /// Loads the client certificate and key, for mutual TLS.
        ///
        /// @param keyStore the key store
        /// @param keySecret the password of the keys
        /// @return this builder
        /// @throws IllegalStateException when the keys cannot be loaded
        public Builder key(KeyStore keyStore, char[] keySecret) {
            try {
                inner.loadKeyMaterial(keyStore, keySecret);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
                throw new IllegalStateException(ex);
            }
        }

        /// Loads the client certificates and keys, for mutual TLS, choosing among them per connection.
        ///
        /// @param keyStore the key store
        /// @param keySecret the password of the keys
        /// @param aliasStrategy chooses the key alias to present
        /// @return this builder
        /// @throws IllegalStateException when the keys cannot be loaded
        public Builder key(KeyStore keyStore, char[] keySecret, PrivateKeyStrategy aliasStrategy) {
            try {
                inner.loadKeyMaterial(keyStore, keySecret, aliasStrategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException ex) {
                throw new IllegalStateException(ex);
            }
        }

        /// Trusts the JDK default trust store, plus the certificates the strategy accepts.
        ///
        /// @param strategy accepts certificates the trust store would reject, or `null`
        /// @return this builder
        /// @throws IllegalStateException when the trust store cannot be loaded
        public Builder trust(@Nullable TrustStrategy strategy) {
            try {
                inner.loadTrustMaterial(strategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException ex) {
                throw new IllegalStateException(ex);
            }
        }

        /// Trusts a trust store, plus the certificates the strategy accepts.
        ///
        /// @param keyStore the trust store, or `null` for the JDK default one
        /// @param strategy accepts certificates the trust store would reject, or `null`
        /// @return this builder
        /// @throws IllegalStateException when the trust store cannot be loaded
        public Builder trust(@Nullable KeyStore keyStore, @Nullable TrustStrategy strategy) {
            try {
                inner.loadTrustMaterial(keyStore, strategy);
                return this;
            } catch (NoSuchAlgorithmException | KeyStoreException ex) {
                throw new IllegalStateException(ex);
            }
        }

        /// @param hostnameVerificationPolicy who verifies the hostname; when `null`, `BOTH` if a
        /// [#hostnameVerifier] is set, `BUILTIN` (JSSE only) otherwise
        /// @return this builder
        public Builder hostnameVerificationPolicy(@Nullable HostnameVerificationPolicy hostnameVerificationPolicy) {
            this.hostnameVerificationPolicy = hostnameVerificationPolicy;
            return this;
        }

        /// @param hostnameVerifier the client side hostname verifier, consulted with the `CLIENT` and `BOTH`
        /// policies; when `null`, only JSSE verifies the hostname, and only with the `BUILTIN` and `BOTH`
        /// policies
        /// @return this builder
        public Builder hostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /// @param sslBufferMode the buffer mode, `STATIC` when `null`
        /// @return this builder
        public Builder sslBufferMode(SSLBufferMode sslBufferMode) {
            this.sslBufferMode = sslBufferMode;
            return this;
        }

        /// @param protocols the enabled TLS protocols, e.g. `TLSv1.3`; the JSSE defaults when not set
        /// @return this builder
        public Builder protocols(String... protocols) {
            this.protocols = protocols;
            return this;
        }

        /// @param cipherSuites the enabled cipher suites; the JSSE defaults when not set
        /// @return this builder
        public Builder cipherSuites(String... cipherSuites) {
            this.cipherSuites = cipherSuites;
            return this;
        }

        /// @return the strategy
        /// @throws IllegalStateException when the `SSLContext` cannot be created
        public TlsSocketStrategy build() {
            try {
                return new DefaultClientTlsStrategy(inner.build(), protocols, cipherSuites, sslBufferMode, hostnameVerificationPolicy, hostnameVerifier);
            } catch (NoSuchAlgorithmException | KeyManagementException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
