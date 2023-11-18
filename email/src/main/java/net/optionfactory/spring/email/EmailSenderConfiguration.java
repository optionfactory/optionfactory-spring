package net.optionfactory.spring.email;

import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

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
        @NonNull
        Optional<String> username,
        @NonNull
        Optional<String> password,
        @NonNull
        Optional<Duration> deadAfter) {

    public enum Protocol {
        PLAIN,
        TLS,
        START_TLS_SUPPORTED,
        START_TLS_REQUIRED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean placebo;
        private Duration connectionTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private String host;
        private SSLSocketFactory sslSocketFactory;
        private Protocol protocol;
        private int port;
        private String username;
        private String password;
        private Duration deadAfter;

        public Builder placebo(boolean placebo) {
            this.placebo = placebo;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = timeout;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        public Builder socketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        public Builder deadAfter(@Nullable Duration deadAfter) {
            this.deadAfter = deadAfter;
            return this;
        }

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
                    Optional.ofNullable(username),
                    Optional.ofNullable(password),
                    Optional.ofNullable(deadAfter)
            );
        }
    }

}
