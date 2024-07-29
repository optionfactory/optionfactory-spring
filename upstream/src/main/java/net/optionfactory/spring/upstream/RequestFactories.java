package net.optionfactory.spring.upstream;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;

public class RequestFactories {

    public static HttpClientBuilder pooledClientBuilder(
            Duration connectionTimeout,
            Duration socketTimeout,
            @Nullable Consumer<PoolingHttpClientConnectionManagerBuilder> connectionManagerCustomizer,
            @Nullable LayeredConnectionSocketFactory sslSocketFactory) {

        final var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connectionTimeout.toSeconds(), TimeUnit.SECONDS)
                        .setSocketTimeout((int) socketTimeout.toSeconds(), TimeUnit.SECONDS)
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build());

        if (connectionManagerCustomizer != null) {
            connectionManagerCustomizer.accept(connectionManagerBuilder);
        }
        
        final var connectionManager = connectionManagerBuilder.build();
        return HttpClientBuilder.create().setConnectionManager(connectionManager);
    }

    public static HttpComponentsClientHttpRequestFactory pooled(
            Duration connectionTimeout,
            Duration socketTimeout,
            @Nullable Consumer<PoolingHttpClientConnectionManagerBuilder> connectionManagerCustomizer,
            @Nullable LayeredConnectionSocketFactory sslSocketFactory) {
        final var httpClientBuilder = pooledClientBuilder(connectionTimeout, socketTimeout, connectionManagerCustomizer, sslSocketFactory);
        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }
}
