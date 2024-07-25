package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.validation.Schema;

import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.errors.UpstreamErrorOnReponseHandler;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.faults.UpstreamFaultInterceptor;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockResourcesUpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.MockUpstreamRequestFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.params.UpstreamAnnotatedCookiesInterceptor;
import net.optionfactory.spring.upstream.params.UpstreamAnnotatedHeadersInterceptor;
import net.optionfactory.spring.upstream.params.UpstreamAnnotatedQueryParamsInterceptor;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapMessageHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.UpstreamSoapActionIninitializer;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class UpstreamBuilder<T> {

    protected final Expressions expressions;
    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<UpstreamHttpRequestInitializer> initializers = new ArrayList<>();
    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<UpstreamResponseErrorHandler> responseErrorHandlers = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected final List<HttpServiceArgumentResolver> argumentResolvers = new ArrayList<>();
    protected final Map<Method, Upstream.Logging.Conf> loggingOverrides = new HashMap<>();

    protected final Class<?> klass;
    protected final String upstreamId;
    protected final Map<Method, EndpointDescriptor> endpoints;

    protected UpstreamHttpRequestFactory upstreamRequestFactory;
    protected ClientHttpRequestFactory requestFactory;
    protected Supplier<Object> principal;
    protected InstantSource clock;

    public UpstreamBuilder(Class<T> klass, Optional<BeanResolver> beanResolver, Optional<String> name) {
        this.klass = klass;
        this.expressions = new Expressions(beanResolver.orElse(null));
        this.upstreamId = name.or(() -> Annotations.closest(klass, Upstream.class)
                        .map(a -> a.value()))
                .filter(n -> !n.isBlank())
                .orElse(klass.getSimpleName());
        this.endpoints = Stream.of(klass.getMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge() && !m.isDefault())
                .filter(m -> AnnotationUtils.findAnnotation(m, HttpExchange.class) != null)
                .map(m -> {
                    final var epa = AnnotationUtils.findAnnotation(m, Upstream.Endpoint.class);
                    final var principalIndex = IntStream
                            .range(0, m.getParameters().length)
                            .filter(i -> m.getParameters()[i].isAnnotationPresent(Upstream.Principal.class)
                                    || m.getParameters()[i].getType().isAnnotationPresent(Upstream.Principal.class)
                            )
                            .mapToObj(i -> i)
                            .findFirst();
                    return new EndpointDescriptor(upstreamId, epa == null ? m.getName() : epa.value(), m, principalIndex.orElse(null));
                })
                .collect(Collectors.toMap(EndpointDescriptor::method, ed -> ed));
    }

    public static <T> UpstreamBuilder<T> create(Class<T> klass, Optional<BeanResolver> beanResolver) {
        return new UpstreamBuilder(klass, beanResolver, Optional.empty());
    }

    public static <T> UpstreamBuilder<T> create(Class<T> klass) {
        return new UpstreamBuilder(klass, Optional.empty(), Optional.empty());
    }

    public static <T> UpstreamBuilder<T> named(Class<T> klass, Optional<BeanResolver> beanResolver, String name) {
        return new UpstreamBuilder(klass, beanResolver, Optional.of(name));
    }

    public static <T> UpstreamBuilder<T> named(Class<T> klass, String name) {
        return new UpstreamBuilder(klass, Optional.empty(), Optional.of(name));
    }

    public UpstreamBuilder<T> requestFactoryMockResourcesIf(boolean test) {
        if (!test) {
            return this;
        }
        return requestFactoryMockResources();
    }

    public UpstreamBuilder<T> requestFactoryMockResources() {
        this.upstreamRequestFactory = new MockUpstreamRequestFactory(new MockResourcesUpstreamHttpResponseFactory());
        return this;
    }

    public UpstreamBuilder<T> requestFactoryIf(boolean test, UpstreamHttpResponseFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    public UpstreamBuilder<T> requestFactory(UpstreamHttpResponseFactory factory) {
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
        final var conf = Annotations.closest(klass, Upstream.class).orElseGet(() -> AnnotationUtils.synthesizeAnnotation(Upstream.class));
        this.requestFactory = RequestFactories.create(
                Duration.of(conf.connectionTimeout(), ChronoUnit.SECONDS),
                Duration.of(conf.socketTimeout(), ChronoUnit.SECONDS),
                sslSocketFactory
        );
        return this;
    }

    public UpstreamBuilder<T> requestFactory(LayeredConnectionSocketFactory sslSocketFactory, Duration connectionTimeout, Duration socketTimeout) {
        this.requestFactory = RequestFactories.create(
                connectionTimeout,
                socketTimeout,
                sslSocketFactory
        );
        return this;
    }

    public UpstreamBuilder<T> logging(Method m, Upstream.Logging.Conf c) {
        loggingOverrides.put(m, c);
        return this;
    }

    public UpstreamBuilder<T> logging(Upstream.Logging.Conf c) {
        for (Method m : endpoints.keySet()) {
            loggingOverrides.put(m, c);
        }
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
        this.initializer(new UpstreamSoapActionIninitializer(protocol));
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
                final var multipart = new AllEncompassingFormHttpMessageConverter();
                for (var converter : multipart.getPartConverters()) {
                    if (converter instanceof MappingJackson2HttpMessageConverter j) {
                        j.setObjectMapper(objectMapper);
                    }
                }
                c.add(new ByteArrayHttpMessageConverter());
                c.add(new StringHttpMessageConverter());
                c.add(new ResourceHttpMessageConverter(false));
                c.add(multipart);
                c.add(new MappingJackson2HttpMessageConverter(objectMapper));
            });
        });
        return this;
    }

    public UpstreamBuilder<T> restClient(Consumer<RestClient.Builder> c) {
        restClientCustomizers.add(c);
        return this;
    }

    public UpstreamBuilder<T> baseUri(URI baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri.toString()));
        return this;
    }

    public UpstreamBuilder<T> baseUri(String baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri));
        return this;
    }

    public UpstreamBuilder<T> initializerIf(boolean test, UpstreamHttpRequestInitializer initializer) {
        if (!test) {
            return this;
        }
        return initializer(initializer);
    }

    public UpstreamBuilder<T> initializer(UpstreamHttpRequestInitializer initializer) {
        this.initializers.add(initializer);
        return this;
    }

    public UpstreamBuilder<T> interceptorIf(boolean test, UpstreamHttpInterceptor interceptor) {
        if (!test) {
            return this;
        }
        return interceptor(interceptor);
    }

    public UpstreamBuilder<T> interceptor(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    public UpstreamBuilder<T> responseErrorHandler(UpstreamResponseErrorHandler eh) {
        responseErrorHandlers.add(eh);
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

    public T build(ObservationRegistry observations, Consumer<Object> publisher) {
        Assert.notNull(observations, "observations must be configured (ObservationRegistry.NOOP?)");
        Assert.notNull(publisher, "publisher must be configured");
        final var clockOrDefault = this.clock != null ? this.clock : InstantSource.system();
        final var principalOrDefault = this.principal != null ? this.principal : new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        };

        final var scopeHandler = new ThreadLocalScopeHandler(principalOrDefault, clockOrDefault, endpoints, expressions, observations, publisher);
        if (requestFactory == null && upstreamRequestFactory == null) {
            this.requestFactory((LayeredConnectionSocketFactory) null);
        }
        Assert.state(upstreamRequestFactory == null || requestFactory == null, "either upstreamRequestFactory or requestFactory must be configured");

        if (upstreamRequestFactory != null) {
            upstreamRequestFactory.preprocess(klass, expressions, endpoints);
        }

        final var bufferedRequestFactory = new BufferingClientHttpRequestFactory(
                upstreamRequestFactory != null
                        ? scopeHandler.adapt(upstreamRequestFactory)
                        : requestFactory
        );

        final var rcb = RestClient.builder().requestFactory(bufferedRequestFactory);
        restClientCustomizers.forEach(c -> c.accept(rcb));

        initializers.stream()
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .map(scopeHandler::adapt)
                .forEach(rcb::requestInitializer);

        final var initializedInterceptors = Stream.concat(interceptors.stream(),
                        Stream.of(
                                new UpstreamAnnotatedHeadersInterceptor(),
                                new UpstreamAnnotatedCookiesInterceptor(),
                                new UpstreamAnnotatedQueryParamsInterceptor(),
                                new UpstreamLoggingInterceptor(loggingOverrides),
                                new UpstreamFaultInterceptor(publisher, observations)
                        ))
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .toList();

        rcb.requestInterceptor(scopeHandler.adapt(initializedInterceptors));

        Stream.concat(
                        Stream.of(new UpstreamErrorOnReponseHandler()),
                        responseErrorHandlers.stream())
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .map(scopeHandler::adapt)
                .forEach(rcb::defaultStatusHandler);

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
