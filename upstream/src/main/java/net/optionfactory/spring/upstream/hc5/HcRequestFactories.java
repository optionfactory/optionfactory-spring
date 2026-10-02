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
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/// Creates request factories backed by a pooled Apache HttpComponents 5 client.
///
/// Usually reached through [net.optionfactory.spring.upstream.UpstreamBuilder#requestFactoryHttpComponents],
/// whose customizer receives the [Builder]:
///
/// ```java
/// UpstreamBuilder.create(PaymentsClient.class)
///         .requestFactoryHttpComponents(c -> c
///                 .connectionTimeout(Duration.ofSeconds(2))
///                 .socketTimeout(Duration.ofSeconds(10))
///                 .tlsSocketStrategy(tls -> tls.key(keyStore, keyPassword).build()))
///         ...
/// ```
public class HcRequestFactories {

    /// @return a builder with no customization
    public static Builder builder() {
        return new Builder();
    }

    /// Configures the HttpComponents client of a request factory.
    ///
    /// Starting from the `@Upstream.HttpComponents` configuration of the client interface (or its
    /// defaults), the customizations registered here are applied in registration order, each group after
    /// the corresponding annotation values, so they win over the annotation: socket settings start from
    /// `SO_KEEPALIVE` enabled, connection settings from the annotation timeouts, the connection manager
    /// from its pool sizes and the client builder from its `disable*` flags.
    ///
    /// Every built factory owns a new connection pool.
    public static class Builder {

        private final List<Consumer<SocketConfig.Builder>> socketConfigCustomizers = new ArrayList<>();
        private final List<Consumer<ConnectionConfig.Builder>> connectionConfigCustomizers = new ArrayList<>();
        private final List<Consumer<PoolingHttpClientConnectionManagerBuilder>> connectionManagerCustomizers = new ArrayList<>();
        private final List<Consumer<HttpClientBuilder>> clientBuilderCustomizers = new ArrayList<>();

        /// @param c customizes the pooling connection manager
        /// @return this builder
        public Builder connectionManager(Consumer<PoolingHttpClientConnectionManagerBuilder> c) {
            this.connectionManagerCustomizers.add(c);
            return this;
        }

        /// Configures TLS through an [HcSocketStrategies.Builder], invoked whenever a factory is built.
        ///
        /// @param customizer builds the TLS strategy from a fresh builder
        /// @return this builder
        public Builder tlsSocketStrategy(Function<HcSocketStrategies.Builder, TlsSocketStrategy> customizer) {
            connectionManager(c -> c.setTlsSocketStrategy(customizer.apply(HcSocketStrategies.builder())));
            return this;
        }

        /// @param strategy the TLS strategy, or `null` for the HttpComponents default (see
        /// [HcSocketStrategies#defaults])
        /// @return this builder
        public Builder tlsSocketStrategy(@Nullable TlsSocketStrategy strategy) {
            return connectionManager(c -> c.setTlsSocketStrategy(strategy));
        }

        /// @param max the maximum number of pooled connections
        /// @return this builder
        public Builder maxConnections(int max) {
            return connectionManager(c -> c.setMaxConnTotal(max));
        }

        /// @param max the maximum number of pooled connections per route
        /// @return this builder
        public Builder maxConnectionsPerRoute(int max) {
            return connectionManager(c -> c.setMaxConnPerRoute(max));
        }

        /// @param c customizes the socket configuration
        /// @return this builder
        public Builder socketConfig(Consumer<SocketConfig.Builder> c) {
            this.socketConfigCustomizers.add(c);
            return this;
        }

        /// @param value whether to enable `SO_KEEPALIVE`, enabled by default
        /// @return this builder
        public Builder socketKeepAlive(boolean value) {
            return socketConfig(c -> c.setSoKeepAlive(value));
        }

        /// @param value whether to enable `TCP_NODELAY`
        /// @return this builder
        public Builder socketTcpNoDelay(boolean value) {
            return socketConfig(c -> c.setTcpNoDelay(value));
        }

        /// @param address the SOCKS proxy to connect through
        /// @return this builder
        public Builder socketSocksProxy(SocketAddress address) {
            return socketConfig(c -> c.setSocksProxyAddress(address));
        }

        /// @param c customizes the connection configuration
        /// @return this builder
        public Builder connectionConfig(Consumer<ConnectionConfig.Builder> c) {
            this.connectionConfigCustomizers.add(c);
            return this;
        }

        /// @param d the timeout for establishing a connection, with millisecond precision
        /// @return this builder
        public Builder connectionTimeout(Duration d) {
            return connectionConfig(c -> c.setConnectTimeout(d.toMillis(), TimeUnit.MILLISECONDS));
        }

        /// @param d the timeout for waiting for data on an established connection, with millisecond
        /// precision
        /// @return this builder
        public Builder socketTimeout(Duration d) {
            return connectionConfig(c -> c.setSocketTimeout((int) d.toMillis(), TimeUnit.MILLISECONDS));
        }

        /// @param d how long a connection can be reused, or `null` (the default) for no limit
        /// @return this builder
        public Builder connectionTimeToLive(Duration d) {
            return connectionConfig(c -> {
                if (d == null) {
                    c.setTimeToLive(null);
                } else {
                    c.setTimeToLive(d.toMillis(), TimeUnit.MILLISECONDS);
                }
            });
        }

        /// @param d the inactivity after which a pooled connection is validated before reuse, or `null`
        /// (the default) for no validation
        /// @return this builder
        public Builder connectionValidateAfterInactivity(Duration d) {
            return connectionConfig(c -> {
                if (d == null) {
                    c.setValidateAfterInactivity(null);
                } else {
                    c.setValidateAfterInactivity(d.toMillis(), TimeUnit.MILLISECONDS);
                }
            });
        }

        /// @param c customizes the client builder
        /// @return this builder
        public Builder clientBuilder(Consumer<HttpClientBuilder> c) {
            this.clientBuilderCustomizers.add(c);
            return this;
        }

        /// @param strategy decides whether a connection can be kept alive
        /// @return this builder
        public Builder connectionReuseStrategy(ConnectionReuseStrategy strategy) {
            return clientBuilder(c -> c.setConnectionReuseStrategy(strategy));
        }

        /// @param proxy the HTTP proxy every request goes through
        /// @return this builder
        public Builder proxy(HttpHost proxy) {
            return clientBuilder(c -> c.setProxy(proxy));
        }

        /// @param selector chooses the proxy of each request
        /// @return this builder
        public Builder proxySelector(ProxySelector selector) {
            return clientBuilder(c -> c.setProxySelector(selector));
        }

        /// @param strategy chooses how to authenticate against the proxy
        /// @return this builder
        public Builder proxyAuthenticator(AuthenticationStrategy strategy) {
            return clientBuilder(c -> c.setProxyAuthenticationStrategy(strategy));
        }

        /// Disables the caching of authentication schemes between requests.
        ///
        /// @return this builder
        public Builder disableAuthCaching() {
            return clientBuilder(c -> c.disableAuthCaching());
        }

        /// Disables the automatic retries of failed requests.
        ///
        /// @return this builder
        public Builder disableAutomaticRetries() {
            return clientBuilder(c -> c.disableAutomaticRetries());
        }

        /// Disables the tracking of the connection state (`HttpClientBuilder.disableConnectionState`).
        ///
        /// @return this builder
        public Builder disableConnectionState() {
            return clientBuilder(c -> c.disableConnectionState());
        }

        /// Disables the transparent request of compressed responses and their decompression.
        ///
        /// @return this builder
        public Builder disableContentCompression() {
            return clientBuilder(c -> c.disableContentCompression());
        }

        /// Disables the cookie store: cookies set by the upstream are not sent back.
        ///
        /// @return this builder
        public Builder disableCookieManagement() {
            return clientBuilder(c -> c.disableCookieManagement());
        }

        /// Disables the default `User-Agent` header.
        ///
        /// @return this builder
        public Builder disableDefaultUserAgent() {
            return clientBuilder(c -> c.disableDefaultUserAgent());
        }

        /// Disables following redirects: `3xx` responses reach the client.
        ///
        /// @return this builder
        public Builder disableRedirectHandling() {
            return clientBuilder(c -> c.disableRedirectHandling());
        }

        private HttpComponentsClientHttpRequestFactory buildFactory(
                Duration connTimeout, Duration sockTimeout,
                int maxConnTotal, int maxConnPerRoute,
                Consumer<HttpClientBuilder> clientConfigurer) {
            final var socketConfigBuilder = SocketConfig.custom().setSoKeepAlive(true);
            for (final var socketConfigCustomizer : socketConfigCustomizers) {
                socketConfigCustomizer.accept(socketConfigBuilder);
            }
            final var connectionConfigBuilder = ConnectionConfig.custom()
                    .setConnectTimeout(connTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .setSocketTimeout((int) sockTimeout.toMillis(), TimeUnit.MILLISECONDS);
            for (final var connectionConfigCustomizer : connectionConfigCustomizers) {
                connectionConfigCustomizer.accept(connectionConfigBuilder);
            }
            final var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(connectionConfigBuilder.build())
                    .setDefaultSocketConfig(socketConfigBuilder.build())
                    .setMaxConnTotal(maxConnTotal)
                    .setMaxConnPerRoute(maxConnPerRoute);
            for (final var connectionManagerCustomizer : connectionManagerCustomizers) {
                connectionManagerCustomizer.accept(connectionManagerBuilder);
            }
            final var clientBuilder = HttpClientBuilder.create().setConnectionManager(connectionManagerBuilder.build());
            clientConfigurer.accept(clientBuilder);
            for (final var clientBuilderCustomizer : clientBuilderCustomizers) {
                clientBuilderCustomizer.accept(clientBuilder);
            }
            return new HttpComponentsClientHttpRequestFactory(clientBuilder.build());
        }

        /// Builds a standalone factory, e.g. for a plain `RestClient`. The `@Upstream.HttpComponents`
        /// annotation is not read: its default values apply, under the customizations.
        ///
        /// @param buffering `BUFFERED` to wrap the factory in spring's `BufferingClientHttpRequestFactory`,
        /// anything else for the plain HttpComponents factory
        /// @return the factory
        public ClientHttpRequestFactory build(Buffering buffering) {
            final var defaults = AnnotationUtils.synthesizeAnnotation(HttpComponents.class);
            final var connTimeout = Duration.parse(defaults.connectionTimeout());
            final var sockTimeout = Duration.parse(defaults.socketTimeout());
            final var maxConnections = Integer.parseInt(defaults.maxConnections());
            final var maxConnectionsPerRoute = Integer.parseInt(defaults.maxConnectionsPerRoute());
            final var f = buildFactory(connTimeout, sockTimeout, maxConnections, maxConnectionsPerRoute, cb -> {});
            return switch (buffering) {
                case BUFFERED ->
                    new BufferingClientHttpRequestFactory(f);
                case UNBUFFERED, UNBUFFERED_STREAMING ->
                    f;
            };
        }

        /// Builds the provider [net.optionfactory.spring.upstream.UpstreamBuilder] uses, which reads the
        /// `@Upstream.HttpComponents` annotation of the client interface when the client is built.
        ///
        /// @param buffering `UNBUFFERED` for the plain HttpComponents factory; anything else wraps it in a
        /// [BufferingUpstreamHttpRequestFactory], which buffers each response according to its endpoint
        /// @return the provider
        public RequestFactoryProvider buildConfigurer(Buffering buffering) {
            return (scopeHandler, klass, expressions, endpoints) -> {
                final var conf = Annotations.closest(klass, Upstream.HttpComponents.class).orElseGet(() -> AnnotationUtils.synthesizeAnnotation(HttpComponents.class));
                final var connTimeout = Duration.parse(expressions.string(conf.connectionTimeout(), conf.connectionTimeoutType()).evaluate(expressions.context()));
                final var sockTimeout = Duration.parse(expressions.string(conf.socketTimeout(), conf.socketTimeoutType()).evaluate(expressions.context()));
                final var maxConnections = poolSize(expressions, conf.maxConnections(), conf.maxConnectionsType());
                final var maxConnectionsPerRoute = poolSize(expressions, conf.maxConnectionsPerRoute(), conf.maxConnectionsPerRouteType());

                final var f = buildFactory(connTimeout, sockTimeout, maxConnections, maxConnectionsPerRoute, cb -> {
                    if (conf.disableAuthCaching()) {
                        cb.disableAuthCaching();
                    }
                    if (conf.disableAutomaticRetries()) {
                        cb.disableAutomaticRetries();
                    }
                    if (conf.disableConnectionState()) {
                        cb.disableConnectionState();
                    }
                    if (conf.disableContentCompression()) {
                        cb.disableContentCompression();
                    }
                    if (conf.disableCookieManagement()) {
                        cb.disableCookieManagement();
                    }
                    if (conf.disableDefaultUserAgent()) {
                        cb.disableDefaultUserAgent();
                    }
                    if (conf.disableRedirectHandling()) {
                        cb.disableRedirectHandling();
                    }
                });
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

        private static int poolSize(Expressions expressions, String value, Expressions.Type type) {
            return switch (type) {
                case STATIC ->
                    Integer.parseInt(value);
                case EXPRESSION ->
                    expressions.parse(value).getValue(expressions.context(), int.class);
                case TEMPLATED ->
                    expressions.parseTemplated(value).getValue(expressions.context(), int.class);
            };
        }
    }
}
