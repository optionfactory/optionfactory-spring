package net.optionfactory.spring.email;

import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/// How an [EmailSender] reaches the smtp server, and how long it keeps trying.
///
/// Build it with [#builder()], which applies the defaults:
///
/// ```java
/// final var conf = EmailSenderConfiguration.builder()
///         .host("smtp.example.com")
///         .port(587)
///         .protocol(EmailSenderConfiguration.Protocol.START_TLS_REQUIRED)
///         .username("user")
///         .password("secret")
///         .deadAfter(Duration.ofDays(1))
///         .build();
/// ```
///
/// @param placebo when true, nothing is sent: spooled emails are handled as if delivered
/// @param host the smtp server host
/// @param port the smtp server port
/// @param protocol how the connection is secured
/// @param connectionTimeout the socket connection timeout
/// @param readTimeout the socket read timeout
/// @param writeTimeout the socket write timeout
/// @param sslSocketFactory the factory for TLS sockets, the JDK default when empty
/// @param checkServerIdentity whether the server hostname is checked against its certificate;
/// when false every certificate is also trusted
/// @param username the smtp user, authentication is enabled only when present
/// @param password the smtp password, used only when a username is present
/// @param deadAfter how long an email may stay in the spool before it is given up on, forever when
/// empty
public record EmailSenderConfiguration(
        boolean placebo,
        @NonNull
        String host,
        int port,
        @NonNull
        Protocol protocol,
        @NonNull
        Duration connectionTimeout,
        @NonNull
        Duration readTimeout,
        @NonNull
        Duration writeTimeout,
        @NonNull
        Optional<SSLSocketFactory> sslSocketFactory,
        boolean checkServerIdentity,
        @NonNull
        Optional<String> username,
        @NonNull
        Optional<String> password,
        @NonNull
        Optional<Duration> deadAfter) {

    /// How the connection to the smtp server is secured.
    public enum Protocol {
        /// Unencrypted smtp.
        PLAIN,
        /// smtp over a TLS connection from the start (`smtps`, usually on port 465).
        TLS,
        /// smtp upgraded with STARTTLS when the server offers it, unencrypted otherwise.
        START_TLS_SUPPORTED,
        /// smtp upgraded with STARTTLS, failing when the server does not offer it.
        START_TLS_REQUIRED;
    }

    /// @return a builder with the default timeouts and server identity checks enabled
    public static Builder builder() {
        return new Builder();
    }

    /// Builds an [EmailSenderConfiguration]. `host`, `port` and `protocol` are mandatory;
    /// everything else has a default.
    public static class Builder {

        private boolean placebo;
        private Duration connectionTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private String host;
        private SSLSocketFactory sslSocketFactory;
        private boolean checkServerIdentity = true;
        private Protocol protocol;
        private int port;
        private String username;
        private String password;
        private Duration deadAfter;

        /// A placebo sender goes through the whole spool lifecycle without connecting to the
        /// server, which suits development and test environments.
        ///
        /// @param placebo true to skip the actual delivery, default false
        /// @return this builder
        public Builder placebo(boolean placebo) {
            this.placebo = placebo;
            return this;
        }

        /// @param host the smtp server host, mandatory
        /// @return this builder
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /// @param port the smtp server port, mandatory
        /// @return this builder
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /// @param protocol how the connection is secured, mandatory
        /// @return this builder
        public Builder protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        /// @param timeout the socket connection timeout, default 30 seconds
        /// @return this builder
        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = timeout;
            return this;
        }

        /// @param timeout the socket read timeout, default 60 seconds
        /// @return this builder
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /// @param timeout the socket write timeout, default 60 seconds
        /// @return this builder
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        /// Replaces the JDK default `SSLSocketFactory`, e.g. to trust a private certificate
        /// authority. Ignored with [Protocol#PLAIN].
        ///
        /// @param sslSocketFactory the factory for TLS sockets
        /// @return this builder
        public Builder socketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        /// Disabling the check also trusts every certificate the server presents, which leaves the
        /// connection open to interception: only meant for test servers. Ignored with
        /// [Protocol#PLAIN].
        ///
        /// @param checkServerIdentity whether the server certificate is verified, default true
        /// @return this builder
        public Builder checkServerIdentity(boolean checkServerIdentity) {
            this.checkServerIdentity = checkServerIdentity;
            return this;
        }

        /// @param username the smtp user, `null` to connect without authenticating
        /// @return this builder
        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        /// @param password the smtp password, ignored without a username
        /// @return this builder
        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        /// Bounds how long a failing email is retried. The age is measured from the last
        /// modification of the spooled file, i.e. from when it was spooled.
        ///
        /// @param deadAfter the maximum time in the spool, `null` to retry forever
        /// @return this builder
        public Builder deadAfter(@Nullable Duration deadAfter) {
            this.deadAfter = deadAfter;
            return this;
        }

        /// @return the configuration
        /// @throws IllegalArgumentException when `host`, `port` or `protocol` is missing
        public EmailSenderConfiguration build() {
            Assert.notNull(host, "host must be configured");
            Assert.isTrue(port != 0, "port must be configured");
            Assert.notNull(protocol, "protocol must be configured");
            return new EmailSenderConfiguration(
                    placebo,
                    host,
                    port,
                    protocol,
                    connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(30),
                    readTimeout != null ? readTimeout : Duration.ofSeconds(60),
                    writeTimeout != null ? writeTimeout : Duration.ofSeconds(60),
                    Optional.ofNullable(sslSocketFactory),
                    checkServerIdentity,
                    Optional.ofNullable(username),
                    Optional.ofNullable(password),
                    Optional.ofNullable(deadAfter)
            );
        }
    }

}
