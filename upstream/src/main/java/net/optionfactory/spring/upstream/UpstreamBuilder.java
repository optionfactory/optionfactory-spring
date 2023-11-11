package net.optionfactory.spring.upstream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapMessageHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.UpstreamSoapActionInterceptor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class UpstreamBuilder<T> {

    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected ClientHttpRequestFactory requestFactory;
    protected final Class<?> klass;
    protected final Upstream conf;
    protected Supplier<Object> principal;
    private InstantSource clock;

    public UpstreamBuilder(Class<T> klass) {
        this.klass = klass;
        this.conf = klass.getAnnotation(Upstream.class) != null ? klass.getAnnotation(Upstream.class) : AnnotationUtils.synthesizeAnnotation(Upstream.class);
    }

    public static <T> UpstreamBuilder<T> create(Class<T> klass) {
        return new UpstreamBuilder(klass);
    }

    public UpstreamBuilder<T> requestFactory(ClientHttpRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        return this;
    }

    public UpstreamBuilder<T> requestFactory(LayeredConnectionSocketFactory sslSocketFactory) {
        final var httpClientBuilder = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(conf.connectionTimeout(), TimeUnit.SECONDS)
                                .setSocketTimeout(conf.socketTimeout(), TimeUnit.SECONDS)
                                .build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build());
        this.requestFactory = new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
        return this;
    }

    public UpstreamBuilder<T> soap(Protocol protocol, SoapHeaderWriter headerWriter, Class<?> clazz, Class<?>... more) {
        try {
            final var contextPaths = Stream
                    .concat(Stream.of(clazz), Stream.of(more))
                    .map(k -> k.getPackageName())
                    .collect(Collectors.joining(":"));
            return soap(protocol, headerWriter, JAXBContext.newInstance(contextPaths));
        } catch (JAXBException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public UpstreamBuilder<T> soap(Protocol protocol, SoapHeaderWriter headerWriter, JAXBContext context) {
        if (protocol == Protocol.SOAP_1_1) {
            this.interceptors.add(new UpstreamSoapActionInterceptor());
        }
        restClientCustomizers.add(b -> {
            b.messageConverters(c -> {
                c.clear();
                c.add(new SoapMessageHttpMessageConverter(protocol));
                c.add(new SoapJaxbHttpMessageConverter(protocol, context, headerWriter));
            });
        });
        return this;
    }

    public UpstreamBuilder<T> restClient(Consumer<RestClient.Builder> c) {
        restClientCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder<T> intercept(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    public UpstreamBuilder<T> serviceProxy(Consumer<HttpServiceProxyFactory.Builder> c) {
        serviceProxyCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder<T> clock(InstantSource clock) {
        this.clock = clock;
        return this;
    }

    public UpstreamBuilder<T> principal(Supplier<Object> principal) {
        this.principal = principal;
        return this;
    }

    public T build() {
        final var clockOrDefault = this.clock != null ? this.clock : InstantSource.system();
        final var upstreamId = !conf.value().isBlank() ? conf.value() : klass.getSimpleName();
        final var principalOrDefault = this.principal != null ? this.principal : new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        };
        final var scopeHandler = new ThreadLocalScopeHandler(upstreamId, principalOrDefault, clockOrDefault);

        final var wrappedRequestFactory = new BufferingClientHttpRequestFactory(requestFactory);

        final var rcb = RestClient.builder().requestFactory(wrappedRequestFactory);
        restClientCustomizers.forEach(c -> c.accept(rcb));
        final var is = interceptors.stream().peek(i -> i.preprocess(klass, wrappedRequestFactory)).map(scopeHandler::adapt).toList();
        rcb.requestInterceptors(ris -> ris.addAll(is));

        final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        rcb.messageConverters(mcs -> {
            messageConverters.addAll(mcs);
        });

        final var serviceProxyFactoryBuilder = HttpServiceProxyFactory.builder().exchangeAdapter(RestClientAdapter.create(rcb.build()));
        serviceProxyCustomizers.forEach(c -> c.accept(serviceProxyFactoryBuilder));

        final var client = serviceProxyFactoryBuilder.build().createClient(klass);
        final var p = new ProxyFactory();
        p.setTarget(client);
        p.setInterfaces(klass);
        p.addAdvice(scopeHandler.interceptor(messageConverters));
        return (T) p.getProxy();
    }

}
