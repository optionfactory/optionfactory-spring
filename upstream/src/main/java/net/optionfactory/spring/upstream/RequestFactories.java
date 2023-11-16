package net.optionfactory.spring.upstream;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;

public class RequestFactories {

    public static HttpComponentsClientHttpRequestFactory create(Duration connectionTimeout, Duration socketTimeout, @Nullable LayeredConnectionSocketFactory sslSocketFactory) {
        final var httpClientBuilder = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(connectionTimeout.toSeconds(), TimeUnit.SECONDS)
                                .setSocketTimeout((int) socketTimeout.toSeconds(), TimeUnit.SECONDS)
                                .build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build());
        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }
}
