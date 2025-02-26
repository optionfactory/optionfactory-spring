package net.optionfactory.spring.upstream.hc5;

import java.net.ProxySelector;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.Upstream.HttpComponents;
import net.optionfactory.spring.upstream.UpstreamBuilder.RequestFactoryProvider;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.buffering.BufferingUpstreamHttpRequestFactory;
import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;

public class HcRequestFactories {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Consumer<SocketConfig.Builder>> socketConfigCustomizers = new ArrayList<>();
        private final List<Consumer<ConnectionConfig.Builder>> connectionConfigCustomizers = new ArrayList<>();
        private final List<Consumer<PoolingHttpClientConnectionManagerBuilder>> connectionManagerCustomizers = new ArrayList<>();
        private final List<Consumer<HttpClientBuilder>> clientBuilderCustomizers = new ArrayList<>();

        public Builder connectionManager(Consumer<PoolingHttpClientConnectionManagerBuilder> c) {
            this.connectionManagerCustomizers.add(c);
            return this;
        }

        public Builder tlsSocketStrategy(Function<HcSocketStrategies.Builder, TlsSocketStrategy> customizer) {
            connectionManager(c -> c.setTlsSocketStrategy(customizer.apply(HcSocketStrategies.builder())));
            return this;
        }

        public Builder tlsSocketStrategy(@Nullable TlsSocketStrategy strategy) {
            return connectionManager(c -> c.setTlsSocketStrategy(strategy));
        }

        public Builder maxConnections(int max) {
            return connectionManager(c -> c.setMaxConnTotal(max));
        }

        public Builder maxConnectionsPerRoute(int max) {
            return connectionManager(c -> c.setMaxConnPerRoute(max));
        }

        public Builder socketConfig(Consumer<SocketConfig.Builder> c) {
            this.socketConfigCustomizers.add(c);
            return this;
        }

        public Builder socketKeepAlive(boolean value) {
            return socketConfig(c -> c.setSoKeepAlive(value));
        }

        public Builder socketTcpNoDelay(boolean value) {
            return socketConfig(c -> c.setTcpNoDelay(value));
        }

        public Builder socketSocksProxy(SocketAddress address) {
            return socketConfig(c -> c.setSocksProxyAddress(address));
        }

        public Builder connectionConfig(Consumer<ConnectionConfig.Builder> c) {
            this.connectionConfigCustomizers.add(c);
            return this;
        }

        public Builder connectionTimeout(Duration d) {
            return connectionConfig(c -> c.setConnectTimeout(d.toSeconds(), TimeUnit.SECONDS));
        }

        public Builder socketTimeout(Duration d) {
            return connectionConfig(c -> c.setSocketTimeout((int) d.toSeconds(), TimeUnit.SECONDS));
        }

        public Builder connectionTimeToLive(Duration d) {
            return connectionConfig(c -> {
                if (d == null) {
                    c.setTimeToLive(null);
                } else {
                    c.setTimeToLive(d.toSeconds(), TimeUnit.SECONDS);
                }
            });
        }

        public Builder connectionValidateAfterInactivity(Duration d) {
            return connectionConfig(c -> {
                if (d == null) {
                    c.setValidateAfterInactivity(null);
                } else {
                    c.setValidateAfterInactivity(d.toSeconds(), TimeUnit.SECONDS);
                }
            });
        }

        public Builder clientBuilder(Consumer<HttpClientBuilder> c) {
            this.clientBuilderCustomizers.add(c);
            return this;
        }

        public Builder connectionReuseStrategy(ConnectionReuseStrategy strategy) {
            return clientBuilder(c -> c.setConnectionReuseStrategy(strategy));
        }

        public Builder proxy(HttpHost proxy) {
            return clientBuilder(c -> c.setProxy(proxy));
        }

        public Builder proxySelector(ProxySelector selector) {
            return clientBuilder(c -> c.setProxySelector(selector));
        }

        public Builder proxyAuthenticator(AuthenticationStrategy strategy) {
            return clientBuilder(c -> c.setProxyAuthenticationStrategy(strategy));
        }

        public Builder disableAuthCaching() {
            return clientBuilder(c -> c.disableAuthCaching());
        }

        public Builder disableAutomaticRetries() {
            return clientBuilder(c -> c.disableAutomaticRetries());
        }

        public Builder disableConnectionState() {
            return clientBuilder(c -> c.disableConnectionState());
        }

        public Builder disableContentCompression() {
            return clientBuilder(c -> c.disableContentCompression());
        }

        public Builder disableCookieManagement() {
            return clientBuilder(c -> c.disableCookieManagement());
        }

        public Builder disableDefaultUserAgent() {
            return clientBuilder(c -> c.disableDefaultUserAgent());
        }

        public Builder disableRedirectHandling() {
            return clientBuilder(c -> c.disableRedirectHandling());
        }

        public ClientHttpRequestFactory build(Buffering buffering) {
            final var defaults = AnnotationUtils.synthesizeAnnotation(HttpComponents.class);
            final var connTimeout = Duration.parse(defaults.connectionTimeout());
            final var sockTimeout = Duration.parse(defaults.socketTimeout());
            final var maxConnections = Integer.parseInt(defaults.maxConnections());
            final var maxConnectionsPerRoute = Integer.parseInt(defaults.maxConnectionsPerRoute());

            final var socketConfigBuilder = SocketConfig.custom().setSoKeepAlive(true);

            for (final var socketConfigCustomizer : socketConfigCustomizers) {
                socketConfigCustomizer.accept(socketConfigBuilder);
            }

            final var connectionConfigBuilder = ConnectionConfig.custom()
                    .setConnectTimeout(connTimeout.toSeconds(), TimeUnit.SECONDS)
                    .setSocketTimeout((int) sockTimeout.toSeconds(), TimeUnit.SECONDS);

            for (final var connectionConfigCustomizer : connectionConfigCustomizers) {
                connectionConfigCustomizer.accept(connectionConfigBuilder);
            }

            final var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(connectionConfigBuilder.build())
                    .setDefaultSocketConfig(socketConfigBuilder.build())
                    .setMaxConnTotal(maxConnections)
                    .setMaxConnPerRoute(maxConnectionsPerRoute);

            for (final var connectionManagerCustomizer : connectionManagerCustomizers) {
                connectionManagerCustomizer.accept(connectionManagerBuilder);
            }

            final var clientBuilder = HttpClientBuilder.create().setConnectionManager(connectionManagerBuilder.build());
            for (final var clientBuilderCustomizer : clientBuilderCustomizers) {
                clientBuilderCustomizer.accept(clientBuilder);
            }
            final var f = new HttpComponentsClientHttpRequestFactory(clientBuilder.build());
            return switch (buffering) {
                case BUFFERED ->
                    new BufferingClientHttpRequestFactory(f);
                case UNBUFFERED, UNBUFFERED_STREAMING ->
                    f;
            };
        }

        public RequestFactoryProvider buildConfigurer(Buffering buffering) {
            return (scopeHandler, klass, expressions, endpoints) -> {
                final var conf = Annotations.closest(klass, Upstream.HttpComponents.class).orElseGet(() -> AnnotationUtils.synthesizeAnnotation(HttpComponents.class));
                final var connTimeout = Duration.parse(expressions.string(conf.connectionTimeout(), conf.connectionTimeoutType()).evaluate(expressions.context()));
                final var sockTimeout = Duration.parse(expressions.string(conf.socketTimeout(), conf.socketTimeoutType()).evaluate(expressions.context()));
                final var maxConnections = expressions.parse(conf.maxConnections()).getValue(expressions.context(), int.class);
                final var maxConnectionsPerRoute = expressions.parse(conf.maxConnectionsPerRoute()).getValue(expressions.context(), int.class);

                final var socketConfigBuilder = SocketConfig.custom().setSoKeepAlive(true);

                for (final var socketConfigCustomizer : socketConfigCustomizers) {
                    socketConfigCustomizer.accept(socketConfigBuilder);
                }

                final var connectionConfigBuilder = ConnectionConfig.custom()
                        .setConnectTimeout(connTimeout.toSeconds(), TimeUnit.SECONDS)
                        .setSocketTimeout((int) sockTimeout.toSeconds(), TimeUnit.SECONDS);

                for (final var connectionConfigCustomizer : connectionConfigCustomizers) {
                    connectionConfigCustomizer.accept(connectionConfigBuilder);
                }

                final var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfigBuilder.build())
                        .setDefaultSocketConfig(socketConfigBuilder.build())
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnectionsPerRoute);

                for (final var connectionManagerCustomizer : connectionManagerCustomizers) {
                    connectionManagerCustomizer.accept(connectionManagerBuilder);
                }

                final var clientBuilder = HttpClientBuilder.create().setConnectionManager(connectionManagerBuilder.build());
                if (conf.disableAuthCaching()) {
                    clientBuilder.disableAuthCaching();
                }
                if (conf.disableAutomaticRetries()) {
                    clientBuilder.disableAutomaticRetries();
                }
                if (conf.disableConnectionState()) {
                    clientBuilder.disableConnectionState();
                }
                if (conf.disableContentCompression()) {
                    clientBuilder.disableContentCompression();
                }
                if (conf.disableCookieManagement()) {
                    clientBuilder.disableCookieManagement();
                }
                if (conf.disableDefaultUserAgent()) {
                    clientBuilder.disableDefaultUserAgent();
                }
                if (conf.disableRedirectHandling()) {
                    clientBuilder.disableRedirectHandling();
                }
                for (final var clientBuilderCustomizer : clientBuilderCustomizers) {
                    clientBuilderCustomizer.accept(clientBuilder);
                }
                final var f = new HttpComponentsClientHttpRequestFactory(clientBuilder.build());
                return switch (buffering) {
                    case UNBUFFERED ->
                        f;
                    case BUFFERED, UNBUFFERED_STREAMING -> {
                        final var buffered = new BufferingUpstreamHttpRequestFactory(f);
                        buffered.preprocess(klass, expressions, endpoints);
                        yield scopeHandler.adapt(buffered);
                    }
                };
            };
        }
    }
}
