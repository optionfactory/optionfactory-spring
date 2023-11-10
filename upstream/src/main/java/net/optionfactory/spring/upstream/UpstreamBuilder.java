package net.optionfactory.spring.upstream;

import java.security.KeyStore;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.HostnameVerifier;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public abstract class UpstreamBuilder {

    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<Consumer<HttpClientBuilder>> httpClientCustomizers = new ArrayList<>();
    protected LayeredConnectionSocketFactory sslSocketFactory;
    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected Supplier<Object> principal;
    private InstantSource clock;

    public UpstreamBuilder intercept(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    public UpstreamBuilder httpClient(Consumer<HttpClientBuilder> c) {
        httpClientCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder httpClientSocketFactory(@Nullable LayeredConnectionSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    public UpstreamBuilder httpClientSocketFactoryTrusting(KeyStore keystore, TrustStrategy strategy, HostnameVerifier verifier) {
        this.sslSocketFactory = SocketFactories.trusting(keystore, strategy, verifier);
        return this;
    }

    public UpstreamBuilder httpClientSocketFactoryTrustingAll() {
        this.sslSocketFactory = SocketFactories.trustAll();
        return this;
    }

    public static SSLConnectionSocketFactory system() {
        return null;
    }

    public UpstreamBuilder restClient(Consumer<RestClient.Builder> c) {
        restClientCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder serviceProxy(Consumer<HttpServiceProxyFactory.Builder> c) {
        serviceProxyCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder clock(InstantSource clock) {
        this.clock = clock;
        return this;
    }
    
    public UpstreamBuilder principal(Supplier<Object> principal) {
        this.principal = principal;
        return this;
    }

    public <T> T build(Class<T> klass) {

        final var conf = klass.getAnnotation(Upstream.class) != null ? klass.getAnnotation(Upstream.class) : AnnotationUtils.synthesizeAnnotation(Upstream.class);
        final var clockOrDefault = this.clock != null ? this.clock : InstantSource.system();
        final var upstreamId = !conf.value().isBlank() ? conf.value() : klass.getSimpleName();
        final var principalOrDefault = this.principal != null ? this.principal : new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        };
        final var scopeHandler = new ThreadLocalScopeHandler(upstreamId, principalOrDefault, clockOrDefault);

        final var httpClientBuilder = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(this.sslSocketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setSocketTimeout(conf.socketTimeout(), TimeUnit.SECONDS)
                                .setConnectTimeout(conf.connectionTimeout(), TimeUnit.SECONDS)
                                .build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build());

        httpClientCustomizers.forEach(c -> c.accept(httpClientBuilder));

        final boolean buffering = true;
        final var requestFactory = buffering ? new BufferingClientHttpRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build())) : new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
        final var is = interceptors.stream().peek(i -> i.preprocess(klass, requestFactory)).map(scopeHandler::adapt).toList();

        final var rcb = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptors(ris -> ris.addAll(is));

        configureRestClient(rcb);

        restClientCustomizers.forEach(c -> c.accept(rcb));

        List<HttpMessageConverter<?>> cs = new ArrayList<>();
        rcb.messageConverters(mcs -> {
            cs.addAll(cs);            
        });
                
                
        
        final var serviceProxyFactoryBuilder = HttpServiceProxyFactory.builder().exchangeAdapter(RestClientAdapter.create(rcb.build()));
        serviceProxyCustomizers.forEach(c -> c.accept(serviceProxyFactoryBuilder));


        
        final var client = serviceProxyFactoryBuilder.build().createClient(klass);
        final var p = new ProxyFactory();
        p.setTarget(client);
        p.setInterfaces(klass);
        p.addAdvice(scopeHandler.interceptor(cs));
        return (T) p.getProxy();
    }

    protected abstract void configureRestClient(RestClient.Builder rcb);

}
