package net.optionfactory.spring.upstream.hc5;

import java.net.ProxySelector;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.Upstream.HttpComponents;
import net.optionfactory.spring.upstream.UpstreamBuilder.RequestFactoryConfigurer;
import net.optionfactory.spring.upstream.annotations.Annotations;
import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;

public class HttpComponentsCustomizer {

    private final List<Consumer<SocketConfig.Builder>> socketConfigCustomizers = new ArrayList<>();
    private final List<Consumer<ConnectionConfig.Builder>> connectionConfigCustomizers = new ArrayList<>();
    private final List<Consumer<PoolingHttpClientConnectionManagerBuilder>> connectionManagerCustomizers = new ArrayList<>();
    private final List<Consumer<HttpClientBuilder>> clientBuilderCustomizers = new ArrayList<>();

    public HttpComponentsCustomizer connectionManager(Consumer<PoolingHttpClientConnectionManagerBuilder> c) {
        this.connectionManagerCustomizers.add(c);
        return this;
    }

    public HttpComponentsCustomizer tlsSocketFactory(@Nullable LayeredConnectionSocketFactory sslSocketFactory) {
        return connectionManager(c -> c.setSSLSocketFactory(sslSocketFactory));
    }

    public HttpComponentsCustomizer maxConnections(int max) {
        return connectionManager(c -> c.setMaxConnTotal(max));
    }

    public HttpComponentsCustomizer maxConnectionsPerRoute(int max) {
        return connectionManager(c -> c.setMaxConnPerRoute(max));
    }

    public HttpComponentsCustomizer socketConfig(Consumer<SocketConfig.Builder> c) {
        this.socketConfigCustomizers.add(c);
        return this;
    }

    public HttpComponentsCustomizer socketKeepAlive(boolean value) {
        return socketConfig(c -> c.setSoKeepAlive(value));
    }

    public HttpComponentsCustomizer socketTcpNoDelay(boolean value) {
        return socketConfig(c -> c.setTcpNoDelay(value));
    }

    public HttpComponentsCustomizer socketSocksProxy(SocketAddress address) {
        return socketConfig(c -> c.setSocksProxyAddress(address));
    }

    public HttpComponentsCustomizer connectionConfig(Consumer<ConnectionConfig.Builder> c) {
        this.connectionConfigCustomizers.add(c);
        return this;
    }

    public HttpComponentsCustomizer connectionTimeout(Duration d) {
        return connectionConfig(c -> c.setConnectTimeout(d.toSeconds(), TimeUnit.SECONDS));
    }

    public HttpComponentsCustomizer socketTimeout(Duration d) {
        return connectionConfig(c -> c.setSocketTimeout((int) d.toSeconds(), TimeUnit.SECONDS));
    }

    public HttpComponentsCustomizer connectionTimeToLive(Duration d) {
        return connectionConfig(c -> {
            if (d == null) {
                c.setTimeToLive(null);
            } else {
                c.setTimeToLive(d.toSeconds(), TimeUnit.SECONDS);
            }
        });
    }

    public HttpComponentsCustomizer connectionValidateAfterInactivity(Duration d) {
        return connectionConfig(c -> {
            if (d == null) {
                c.setValidateAfterInactivity(null);
            } else {
                c.setValidateAfterInactivity(d.toSeconds(), TimeUnit.SECONDS);
            }
        });
    }

    public HttpComponentsCustomizer clientBuilder(Consumer<HttpClientBuilder> c) {
        this.clientBuilderCustomizers.add(c);
        return this;
    }

    public HttpComponentsCustomizer connectionReuseStrategy(ConnectionReuseStrategy strategy) {
        return clientBuilder(c -> c.setConnectionReuseStrategy(strategy));
    }

    public HttpComponentsCustomizer proxy(HttpHost proxy) {
        return clientBuilder(c -> c.setProxy(proxy));
    }

    public HttpComponentsCustomizer proxySelector(ProxySelector selector) {
        return clientBuilder(c -> c.setProxySelector(selector));
    }

    public HttpComponentsCustomizer proxyAuthenticator(AuthenticationStrategy strategy) {
        return clientBuilder(c -> c.setProxyAuthenticationStrategy(strategy));
    }

    public HttpComponentsCustomizer disableAuthCaching() {
        return clientBuilder(c -> c.disableAuthCaching());
    }

    public HttpComponentsCustomizer disableAutomaticRetries() {
        return clientBuilder(c -> c.disableAutomaticRetries());
    }

    public HttpComponentsCustomizer disableConnectionState() {
        return clientBuilder(c -> c.disableConnectionState());
    }

    public HttpComponentsCustomizer disableContentCompression() {
        return clientBuilder(c -> c.disableContentCompression());
    }

    public HttpComponentsCustomizer disableCookieManagement() {
        return clientBuilder(c -> c.disableCookieManagement());
    }

    public HttpComponentsCustomizer disableDefaultUserAgent() {
        return clientBuilder(c -> c.disableDefaultUserAgent());
    }

    public HttpComponentsCustomizer disableRedirectHandling() {
        return clientBuilder(c -> c.disableRedirectHandling());
    }

    public HttpComponentsClientHttpRequestFactory toRequestFactory() {
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
        return new HttpComponentsClientHttpRequestFactory(clientBuilder.build());
    }

    public RequestFactoryConfigurer toRequestFactoryConfigurer() {
        return (scopeHandler, klass, expressions, endpoints) -> {
            final var conf = Annotations.closest(klass, Upstream.HttpComponents.class).orElseGet(() -> AnnotationUtils.synthesizeAnnotation(HttpComponents.class));
            final var connTimeout = Duration.parse(expressions.string(conf.connectionTimeout(), conf.connectionTimeoutType()).evaluate(expressions.context()));
            final var sockTimeout = Duration.parse(expressions.string(conf.socketTimeout(), conf.socketTimeoutType()).evaluate(expressions.context()));
            final var maxConnections = expressions.integer(conf.maxConnections()).evaluate(expressions.context());
            final var maxConnectionsPerRoute = expressions.integer(conf.maxConnectionsPerRoute()).evaluate(expressions.context());

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
            return new HttpComponentsClientHttpRequestFactory(clientBuilder.build());
        };
    }
}
