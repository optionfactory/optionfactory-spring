package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.time.Duration;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.validation.Schema;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.HttpMessageConverters;
import net.optionfactory.spring.upstream.mocks.MockResourcesUpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.MockUpstreamRequestFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapMessageHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.UpstreamSoapActionInterceptor;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpResponseFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

public class UpstreamBuilder<T> {

    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected final List<HttpServiceArgumentResolver> argumentResolvers = new ArrayList<>();
    protected UpstreamHttpRequestFactory upstreamRequestFactory;
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

    public UpstreamBuilder<T> requestFactoryMockResourcesIf(boolean test) {
        if (!test) {
            return this;
        }
        return requestFactoryMockResources();
    }

    public UpstreamBuilder<T> requestFactoryMockResources() {
        final var factory = new MockResourcesUpstreamHttpResponseFactory();
        factory.prepare(klass);
        this.upstreamRequestFactory = new MockUpstreamRequestFactory(factory);
        return this;
    }

    public UpstreamBuilder<T> requestFactoryIf(boolean test, UpstreamHttpResponseFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    public UpstreamBuilder<T> requestFactory(UpstreamHttpResponseFactory factory) {
        factory.prepare(klass);
        this.upstreamRequestFactory = new MockUpstreamRequestFactory(factory);
        return this;
    }

    public UpstreamBuilder<T> requestFactoryIf(boolean test, UpstreamHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    public UpstreamBuilder<T> requestFactory(UpstreamHttpRequestFactory factory) {
        this.upstreamRequestFactory = factory;
        return this;
    }

    public UpstreamBuilder<T> requestFactoryIf(boolean test, ClientHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    public UpstreamBuilder<T> requestFactory(ClientHttpRequestFactory factory) {
        this.requestFactory = factory;
        return this;
    }

    public UpstreamBuilder<T> requestFactoryIf(boolean test, LayeredConnectionSocketFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    public UpstreamBuilder<T> requestFactory(LayeredConnectionSocketFactory sslSocketFactory) {
        this.requestFactory = RequestFactories.create(
                Duration.of(conf.connectionTimeout(), ChronoUnit.SECONDS),
                Duration.of(conf.socketTimeout(), ChronoUnit.SECONDS),
                sslSocketFactory
        );
        return this;
    }

    public UpstreamBuilder<T> soap(Protocol protocol, @Nullable Schema schema, @Nullable SoapHeaderWriter headerWriter, Class<?> clazz, Class<?>... more) {
        try {
            final var contextPaths = Stream
                    .concat(Stream.of(clazz), Stream.of(more))
                    .map(k -> k.getPackageName())
                    .collect(Collectors.joining(":"));
            return soap(protocol, schema, headerWriter, JAXBContext.newInstance(contextPaths));
        } catch (JAXBException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public UpstreamBuilder<T> soap(Protocol protocol, @Nullable Schema schema, @Nullable SoapHeaderWriter headerWriter, JAXBContext context) {
        if (protocol == Protocol.SOAP_1_1) {
            this.interceptors.add(new UpstreamSoapActionInterceptor());
        }
        restClientCustomizers.add(b -> {
            b.messageConverters(c -> {
                c.clear();
                c.add(new SoapMessageHttpMessageConverter(protocol));
                c.add(new SoapJaxbHttpMessageConverter(protocol, context, schema, headerWriter));
            });
        });
        return this;
    }

    public UpstreamBuilder<T> json(ObjectMapper objectMapper) {
        restClientCustomizers.add(b -> {
            b.messageConverters(c -> {
                c.clear();
                c.add(new ByteArrayHttpMessageConverter());
                c.add(new StringHttpMessageConverter());
                c.add(new ResourceHttpMessageConverter(false));
                c.add(new AllEncompassingFormHttpMessageConverter());
                c.add(new MappingJackson2HttpMessageConverter(objectMapper));
            });
        });
        return this;
    }

    public UpstreamBuilder<T> restClient(Consumer<RestClient.Builder> c) {
        restClientCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder<T> interceptIf(boolean test, UpstreamHttpInterceptor interceptor) {
        if (!test) {
            return this;
        }
        return intercept(interceptor);
    }

    public UpstreamBuilder<T> intercept(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    public UpstreamBuilder<T> serviceProxy(Consumer<HttpServiceProxyFactory.Builder> c) {
        serviceProxyCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder<T> argumentResolver(HttpServiceArgumentResolver argResolver) {
        this.argumentResolvers.add(argResolver);
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
        final var endpointNames = Stream.of(klass.getDeclaredMethods())
                .collect(Collectors.toMap(
                        m -> m,
                        m -> m.getAnnotation(Upstream.Endpoint.class) != null ? m.getAnnotation(Upstream.Endpoint.class).value() : m.getName()
                ));

        final var scopeHandler = new ThreadLocalScopeHandler(klass, upstreamId, principalOrDefault, clockOrDefault, endpointNames);
        if (requestFactory == null && upstreamRequestFactory == null) {
            this.requestFactory((LayeredConnectionSocketFactory) null);
        }
        Assert.state(upstreamRequestFactory == null || requestFactory == null, "either upstreamRequestFactory or requestFactory must be configured");
        final var bufferedRequestFactory = new BufferingClientHttpRequestFactory(
                upstreamRequestFactory != null
                        ? scopeHandler.adapt(upstreamRequestFactory)
                        : requestFactory
        );

        final var rcb = RestClient.builder().requestFactory(bufferedRequestFactory);
        restClientCustomizers.forEach(c -> c.accept(rcb));
        final var is = interceptors.stream().peek(i -> i.preprocess(klass, bufferedRequestFactory)).map(scopeHandler::adapt).toList();
        rcb.requestInterceptors(ris -> ris.addAll(is));

        final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        rcb.messageConverters(mcs -> {
            messageConverters.addAll(mcs);
        });

        final var serviceProxyFactoryBuilder = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rcb.build()));
        serviceProxyFactoryBuilder.customArgumentResolver(new Upstream.ArgumentResolver());
        serviceProxyCustomizers.forEach(c -> c.accept(serviceProxyFactoryBuilder));
        for (HttpServiceArgumentResolver argumentResolver : argumentResolvers) {
            serviceProxyFactoryBuilder.customArgumentResolver(argumentResolver);
        }
        final var client = serviceProxyFactoryBuilder.build().createClient(klass);
        final var p = new ProxyFactory();
        p.setTarget(client);
        p.setInterfaces(klass);
        p.addAdvice(scopeHandler.interceptor(new HttpMessageConverters(messageConverters)));
        return (T) p.getProxy();
    }

}
