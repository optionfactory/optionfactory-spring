package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.validation.Schema;
import net.optionfactory.spring.upstream.Upstream.Context;
import net.optionfactory.spring.upstream.Upstream.Principal;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext.HttpMessageConverters;
import net.optionfactory.spring.upstream.errors.UpstreamErrorOnReponseHandler;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertInterceptor;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.buffering.BufferingUpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.hc5.HcRequestFactories;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockResourcesUpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.MockUpstreamRequestFactory;
import net.optionfactory.spring.upstream.mocks.MocksCustomizer;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedCookiesInterceptor;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedHeadersInterceptor;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedQueryParamsInterceptor;
import net.optionfactory.spring.upstream.scopes.ScopeHandler;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter.HttpRequestValuesTransformer;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapMessageHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.UpstreamSoapActionIninitializer;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedPathVariableTransformer;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
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
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class UpstreamBuilder<T> {

    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<UpstreamHttpRequestInitializer> initializers = new ArrayList<>();
    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<UpstreamResponseErrorHandler> responseErrorHandlers = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected final List<HttpServiceArgumentResolver> argumentResolvers = new ArrayList<>();
    protected final Map<Method, Upstream.Logging.Conf> loggingOverrides = new HashMap<>();
    protected final Map<String, Object> expressionVars = new HashMap<>();

    protected List<HttpRequestValuesTransformer> requestValuesTransformers = new ArrayList<>();
    protected final Class<?> klass;
    protected final String upstreamId;
    protected final Map<Method, EndpointDescriptor> endpoints;

    protected RequestFactoryProvider rfp;
    protected Supplier<Object> principal;
    protected InstantSource clock;

    protected ObservationRegistry observations;
    protected ConfigurableApplicationContext expressionsApplicationContext;
    protected ApplicationEventPublisher publisher;

    /**
     * Creates an upstream builder. You can use {@code #create} or
     * {@code #named} instead of invoking this constructor directly.
     *
     * @param klass the interface to be implemented
     * @param name the (optional) overridden name
     */
    public UpstreamBuilder(Class<T> klass, Optional<String> name) {
        this.klass = klass;
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

    /**
     * Creates an upstream builder.
     *
     * @param <T> the interface type
     * @param klass the interface to be implemented
     * @return the builder
     */
    public static <T> UpstreamBuilder<T> create(Class<T> klass) {
        return new UpstreamBuilder<>(klass, Optional.empty());
    }

    /**
     * Creates an upstream builder overriding its name.
     *
     * @param <T> the interface type
     * @param klass the interface to be implemented
     * @param name the overridden name
     * @return the builder
     */
    public static <T> UpstreamBuilder<T> named(Class<T> klass, String name) {
        return new UpstreamBuilder<>(klass, Optional.of(name));
    }

    /**
     * Conditionally configures an UpstreamHttpRequestFactory.
     *
     * @param test
     * @param factory
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryIf(boolean test, UpstreamHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    /**
     * Configures an UpstreamHttpRequestFactory.
     *
     * @param factory
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactory(UpstreamHttpRequestFactory factory) {
        Assert.isNull(this.rfp, "request factory is already configured");
        this.rfp = (ScopeHandler sh, Class<?> klass1, Expressions exprs, Map<Method, EndpointDescriptor> eps) -> {
            factory.preprocess(klass1, exprs, eps);
            return sh.adapt(factory);
        };
        return this;
    }

    /**
     * Conditionally configures an UpstreamHttpRequestFactory created using a
     * ClientHttpRequestFactory.
     *
     * @param test the condition
     * @param factory the factory
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryIf(boolean test, ClientHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    /**
     * Configures an UpstreamHttpRequestFactory created using a
     * ClientHttpRequestFactory.
     *
     * @param factory the factory
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactory(ClientHttpRequestFactory factory) {
        Assert.isNull(this.rfp, "request factory is already configured");
        this.rfp = (ScopeHandler sh, Class<?> klass1, Expressions exprs, Map<Method, EndpointDescriptor> eps) -> {
            return factory;
        };
        return this;
    }

    /**
     * Conditionally configures and customizes a request factory using mocks.
     *
     * @param test the condition
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryMockIf(boolean test, Consumer<MocksCustomizer> customizer) {
        if (!test) {
            return this;
        }
        return requestFactoryMock(customizer);
    }

    /**
     * Configures and customizes a request factory using mocks.
     *
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryMock(Consumer<MocksCustomizer> customizer) {
        Assert.notNull(customizer, "customizer must not be null");
        Assert.isNull(this.rfp, "request factory is already configured");
        final var responseFactoryRef = new AtomicReference<UpstreamHttpResponseFactory>();
        final var renderers = new ArrayList<MocksRenderer>();
        final var mc = new MocksCustomizer(responseFactoryRef, renderers);
        customizer.accept(mc);
        final var responseFactory = responseFactoryRef.get();
        this.rfp = (scopeHandler, k, expressions, eps) -> {
            final var rf = responseFactory != null
                    ? responseFactory
                    : new MockResourcesUpstreamHttpResponseFactory(renderers);
            final var hrf = new MockUpstreamRequestFactory(rf);
            hrf.preprocess(k, expressions, eps);
            final var unbuffered = scopeHandler.adapt(hrf);
            final var buffered = new BufferingUpstreamHttpRequestFactory(unbuffered);
            buffered.preprocess(k, expressions, eps);
            return scopeHandler.adapt(buffered);
        };
        return this;
    }

    /**
     * Conditionally configures and customizes a request factory using Apache
     * HTTP Components.
     *
     * @param test the condition
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryHttpComponentsIf(boolean test, Consumer<HcRequestFactories.Builder> customizer) {
        if (!test) {
            return this;
        }
        return requestFactoryHttpComponents(customizer);
    }

    /**
     * Configures and customizes a request factory using Apache HTTP Components.
     *
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> requestFactoryHttpComponents(Consumer<HcRequestFactories.Builder> customizer) {
        Assert.notNull(customizer, "customizer must not be null");
        Assert.isNull(this.rfp, "request factory is already configured");
        final var builder = HcRequestFactories.builder();
        customizer.accept(builder);
        this.rfp = builder.buildConfigurer(Buffering.BUFFERED);
        return this;
    }

    /**
     * Overrides logging configuration provided via annotation for a given
     * method.
     *
     * @param m the method
     * @param c the configuration
     * @return this builder
     */
    public UpstreamBuilder<T> logging(Method m, Upstream.Logging.Conf c) {
        loggingOverrides.put(m, c);
        return this;
    }

    /**
     * Overrides logging configuration provided via annotations.
     *
     * @param c the configuration
     * @return this builder
     */
    public UpstreamBuilder<T> logging(Upstream.Logging.Conf c) {
        for (Method m : endpoints.keySet()) {
            loggingOverrides.put(m, c);
        }
        return this;
    }

    /**
     * Configures default {@code MessageConverter}s for a SOAP client the passed
     * ObjectMapper.
     *
     * @param protocol the protocol to be used
     * @param schema the schema to be used
     * @param headerWriter an optional SoapHeaderWriter
     * @param clazz the class whose package name will be used to create a
     * JAXBContext
     * @param more more classes whose package name will be used to create a
     * JAXBContext
     * @return
     */
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

    /**
     * Configures default {@code MessageConverter}s for a SOAP client the passed
     * ObjectMapper.
     *
     * @param protocol the protocol to be used
     * @param schema the schema to be used
     * @param headerWriter an optional SoapHeaderWriter
     * @param context the jaxb context to be used
     * @return
     */
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

    /**
     * Configures default {@code MessageConverter}s for a REST/HTTP client using
     * the passed ObjectMapper.
     *
     * @param objectMapper
     * @return
     */
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
                c.add(new ResourceHttpMessageConverter(true));
                c.add(multipart);
                c.add(new MappingJackson2HttpMessageConverter(objectMapper));
            });
        });
        return this;
    }

    /**
     * Customizes the RestClient.
     *
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> restClient(Consumer<RestClient.Builder> customizer) {
        restClientCustomizers.add(customizer);
        return this;
    }

    /**
     * Binds an expression variable to be used by Expressions.
     *
     * @param key the key
     * @param value the value
     * @return this builder
     */
    public UpstreamBuilder<T> var(String key, Object value) {
        expressionVars.put(key, value);
        return this;
    }

    /**
     * Configures a base URL.
     *
     * @param baseUri
     * @return this builder
     */
    public UpstreamBuilder<T> baseUri(URI baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri.toString()));
        return this;
    }

    /**
     * Configures a base URL.
     *
     * @param baseUri
     * @return this builder
     */
    public UpstreamBuilder<T> baseUri(String baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri));
        return this;
    }

    /**
     * Conditionally adds a request initializer.
     *
     * @param test the condition
     * @param initializer the initializer
     * @return this builder
     */
    public UpstreamBuilder<T> initializerIf(boolean test, UpstreamHttpRequestInitializer initializer) {
        if (!test) {
            return this;
        }
        return initializer(initializer);
    }

    /**
     * Adds a request initializer.
     *
     * @param initializer the initializer
     * @return this builder
     */
    public UpstreamBuilder<T> initializer(UpstreamHttpRequestInitializer initializer) {
        this.initializers.add(initializer);
        return this;
    }

    /**
     * Conditionally adds an interceptor.
     *
     * @param test the condition
     * @param interceptor the interceptor
     * @return this builder
     */
    public UpstreamBuilder<T> interceptorIf(boolean test, UpstreamHttpInterceptor interceptor) {
        if (!test) {
            return this;
        }
        return interceptor(interceptor);
    }

    /**
     * Adds an interceptor.
     *
     * @param interceptor the interceptor
     * @return this builder
     */
    public UpstreamBuilder<T> interceptor(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    /**
     * Customizes the RequestValues transformers.
     *
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> requestValuesTransformers(Consumer<List<HttpRequestValuesTransformer>> customizer) {
        customizer.accept(requestValuesTransformers);
        return this;
    }

    /**
     * Adds a RequestValues transformer.
     *
     * @param rvt the RequestValues transformer
     * @return this builder
     */
    public UpstreamBuilder<T> requestValuesTransformer(HttpRequestValuesTransformer rvt) {
        this.requestValuesTransformers.add(rvt);
        return this;
    }

    /**
     * Adds a response error handler.
     *
     * @param eh the error handler
     * @return this builder
     */
    public UpstreamBuilder<T> responseErrorHandler(UpstreamResponseErrorHandler eh) {
        responseErrorHandlers.add(eh);
        return this;
    }

    /**
     * Customizes the HttpServiceProxyFactory.
     *
     * @param customizer the customizer
     * @return this builder
     */
    public UpstreamBuilder<T> serviceProxy(Consumer<HttpServiceProxyFactory.Builder> customizer) {
        serviceProxyCustomizers.add(customizer);
        return this;
    }

    /**
     * Registers an argument resolver to the HttpServiceProxy.
     *
     * @param argResolver the resolver
     * @return this builder
     */
    public UpstreamBuilder<T> argumentResolver(HttpServiceArgumentResolver argResolver) {
        this.argumentResolvers.add(argResolver);
        return this;
    }

    /**
     * Configures a clock used to time requests and responses. When the param is
     * null, InstantSource.system() will be used.
     *
     * @param clock
     * @return this builder
     */
    public UpstreamBuilder<T> clock(@Nullable InstantSource clock) {
        this.clock = clock;
        return this;
    }

    /**
     * A supplier for a principal that will be used when an
     * {@code Upstream.Principal} annotated parameter is missing. The supplier
     * can return a null principal, in that case no principal will be available
     * for the request. The supplier can be null, in that case only
     * {@code Upstream.Principal} annotated parameters are taken into
     * consideration.
     *
     * @param principal the supplier
     * @return this builder
     */
    public UpstreamBuilder<T> principal(@Nullable Supplier<Object> principal) {
        this.principal = principal;
        return this;
    }

    public static interface RequestFactoryProvider {

        ClientHttpRequestFactory configure(ScopeHandler sh, Class<?> klass, Expressions expressions, Map<Method, EndpointDescriptor> endpoints);
    }

    /**
     * Configures an ObservationRegistry that will be used to publish client and
     * alert events metrics.
     *
     * @param observations the observation registry
     * @return this builder
     */
    public UpstreamBuilder<T> observations(ObservationRegistry observations) {
        this.observations = observations;
        return this;
    }

    /**
     * Configures an application context that will be made available to
     * expressions.
     *
     * @param ac the application context
     * @return this builder
     */
    public UpstreamBuilder<T> expressions(@Nullable ConfigurableApplicationContext ac) {
        this.expressionsApplicationContext = ac;
        return this;
    }

    /**
     * Configures an ApplicationEventPublisher. The publisher will be used to
     * notify alert events.
     *
     * @param publisher
     * @return this builder
     */
    public UpstreamBuilder<T> publisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        return this;
    }

    /**
     * Configures both an ApplicationEventPublisher the expressions
     * ApplicationContext.
     *
     * @param ac the application context
     * @return this builder
     */
    public UpstreamBuilder<T> applicationContext(@Nullable ConfigurableApplicationContext ac) {
        this.expressionsApplicationContext = ac;
        this.publisher = ac;
        return this;
    }

    /**
     * Builds the upstream client by implementing the configured interface using
     * an HttpServiceProxyFactory.
     *
     * @return the configured client
     */
    public T build() {
        Assert.notNull(rfp, "requestFactory must be configured");
        final var obs = observations != null ? observations : ObservationRegistry.NOOP;
        final var pub = publisher != null ? publisher : new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {

            }
        };
        final var clockOrDefault = this.clock != null ? this.clock : InstantSource.system();
        final var principalOrDefault = this.principal != null ? this.principal : new Supplier<Object>() {
            @Override
            public Object get() {
                return null;
            }
        };
        final var expressions = new Expressions(expressionsApplicationContext, expressionVars);

        final var scopeHandler = new ThreadLocalScopeHandler(principalOrDefault, clockOrDefault, endpoints, expressions, obs, pub);

        final var requestFactory = this.rfp.configure(scopeHandler, klass, expressions, endpoints);

        final var rcb = RestClient.builder().requestFactory(requestFactory);
        restClientCustomizers.forEach(c -> c.accept(rcb));

        initializers.stream()
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .map(scopeHandler::adapt)
                .forEach(rcb::requestInitializer);

        final var initializedInterceptors = Stream.concat(interceptors.stream(),
                Stream.of(new UpstreamAnnotatedHeadersInterceptor(),
                        new UpstreamAnnotatedCookiesInterceptor(),
                        new UpstreamAnnotatedQueryParamsInterceptor(),
                        new UpstreamLoggingInterceptor(loggingOverrides),
                        new UpstreamAlertInterceptor(pub, obs)
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

        final var innerExchangeAdapter = RestClientAdapter.create(rcb.build());

        final var exchangeAdapterChain = new UpstreamHttpExchangeAdapter.Chain(innerExchangeAdapter, Stream.concat(
                Stream.of(new UpstreamAnnotatedPathVariableTransformer()),
                requestValuesTransformers.stream()).toList()
        );
        exchangeAdapterChain.preprocess(klass, expressions, endpoints);
        final var httpExchangeAdapter = scopeHandler.adapt(exchangeAdapterChain);

        final var serviceProxyFactoryBuilder = HttpServiceProxyFactory.builderFor(httpExchangeAdapter);
        serviceProxyFactoryBuilder.customArgumentResolver(new ContextArgumentResolver());

        serviceProxyCustomizers.forEach(c -> c.accept(serviceProxyFactoryBuilder));
        for (HttpServiceArgumentResolver argumentResolver : argumentResolvers) {
            serviceProxyFactoryBuilder.customArgumentResolver(argumentResolver);
        }
        final var client = serviceProxyFactoryBuilder.build().createClient(klass);
        final var p = new ProxyFactory();
        p.setTarget(client);
        p.setInterfaces(klass);
        p.addAdvice(scopeHandler.interceptor(new HttpMessageConverters(messageConverters)));
        @SuppressWarnings("unchecked")
        final var r = (T) p.getProxy();
        return r;
    }

    public static class ContextArgumentResolver implements HttpServiceArgumentResolver {

        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            return parameter.hasParameterAnnotation(Context.class)
                    || parameter.getParameterType().isAnnotationPresent(Context.class)
                    || parameter.hasParameterAnnotation(Principal.class)
                    || parameter.getParameterType().isAnnotationPresent(Principal.class);
        }

    }

}
