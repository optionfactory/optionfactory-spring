package net.optionfactory.spring.upstream;

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
import net.optionfactory.spring.upstream.alerts.UpstreamAlertInterceptor;
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.buffering.BufferingUpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.buffering.InputStreamHttpMessageConverter;
import net.optionfactory.spring.upstream.buffering.StreamHttpMessageConverter;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.errors.UpstreamErrorOnErrorStatusHandler;
import net.optionfactory.spring.upstream.errors.UpstreamErrorOnResponseHandler;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.hc5.HcRequestFactories;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockResourcesUpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.MockUpstreamRequestFactory;
import net.optionfactory.spring.upstream.mocks.MocksCustomizer;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpRequestFactory;
import net.optionfactory.spring.upstream.mocks.UpstreamHttpResponseFactory;
import net.optionfactory.spring.upstream.mocks.rendering.MocksRenderer;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import net.optionfactory.spring.upstream.scopes.ScopeHandler;
import net.optionfactory.spring.upstream.scopes.ThreadLocalScopeHandler;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter;
import net.optionfactory.spring.upstream.scopes.UpstreamHttpExchangeAdapter.HttpRequestValuesTransformer;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.SoapMessageHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.UpstreamSoapActionInitializer;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedCookiesInterceptor;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedHeadersInterceptor;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedPathVariableTransformer;
import net.optionfactory.spring.upstream.values.UpstreamAnnotatedQueryParamsInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// Builds an upstream client: an implementation of a spring `@HttpExchange` interface backed by a
/// `RestClient`, with the logging, alerts, error detection, mocks and expression based request values
/// configured by the [Upstream] annotations.
///
/// A client needs an interface and exactly one request factory, which decides how requests are sent:
/// [#requestFactoryHttpComponents] for real calls, [#requestFactoryMock] to serve the `@Upstream.Mock`
/// resources, or a factory of one's own. It also needs message converters for the payloads, which
/// are not registered by default: [#json], [#xml] and [#soap] configure the usual ones.
///
/// ```java
/// final var client = UpstreamBuilder.create(PaymentsClient.class)
///         .requestFactoryMockIf(mocked, c -> {})
///         .requestFactoryHttpComponentsIf(!mocked, c -> c.connectionTimeout(Duration.ofSeconds(2)))
///         .json(mapper)
///         .initializer(StaticTokenAuthenticator.bearer(token))
///         .applicationContext(ac)
///         .baseUri("https://payments.example.com/api/")
///         .build();
/// ```
///
/// Each built client handles every exchange through, in order: the registered initializers, the
/// registered interceptors, the built-in interceptors (annotated headers, cookies and query params,
/// logging, alerts) and, once the response is back, the registered response error handlers followed
/// by the built-in ones (error statuses, `@Upstream.ErrorOnResponse`).
///
/// A builder is not thread-safe, while the clients it builds are. Being an [UpstreamPrototype], a
/// partially configured builder can be shared and copied with [#builder].
///
/// @param <T> the client interface
public class UpstreamBuilder<T> implements UpstreamPrototype<T> {

    protected Optional<Consumer<HttpMessageConverters.ClientBuilder>> convertersConfigurer = Optional.empty();
    protected final List<Consumer<RestClient.Builder>> restClientCustomizers = new ArrayList<>();
    protected final List<UpstreamHttpRequestInitializer> initializers = new ArrayList<>();
    protected final List<UpstreamHttpInterceptor> interceptors = new ArrayList<>();
    protected final List<UpstreamResponseErrorHandler> responseErrorHandlers = new ArrayList<>();
    protected final List<Consumer<HttpServiceProxyFactory.Builder>> serviceProxyCustomizers = new ArrayList<>();
    protected final List<HttpServiceArgumentResolver> argumentResolvers = new ArrayList<>();

    protected Optional<Upstream.Logging.Conf> loggingOverride = Optional.empty();
    protected final Map<Method, Upstream.Logging.Conf> loggingOverrides = new HashMap<>();
    protected final Map<String, Object> expressionVars = new HashMap<>();

    protected List<HttpRequestValuesTransformer> requestValuesTransformers = new ArrayList<>();

    protected Class<?> klass;
    protected Optional<String> name = Optional.empty();

    protected RequestFactoryProvider rfp;
    protected Supplier<Object> principal;
    protected InstantSource clock;

    protected PayloadsRendering rendering;

    protected ObservationRegistry observations;
    protected ConfigurableApplicationContext expressionsApplicationContext;
    protected ApplicationEventPublisher publisher;

    /// Creates an empty builder; [#create(Class)] and [#named] are the usual entry points.
    public UpstreamBuilder() {
    }

    /// Creates a builder holding a copy of another's configuration. The lists of initializers,
    /// interceptors, handlers, customizers and variables are copied, so that adding to either builder
    /// does not affect the other; the configured objects themselves (request factory, clock, principal
    /// supplier, rendering, application context) are shared.
    ///
    /// @param other the builder to copy
    public UpstreamBuilder(UpstreamBuilder<T> other) {
        this.klass = other.klass;
        this.name = other.name;
        this.rfp = other.rfp;
        this.principal = other.principal;
        this.clock = other.clock;
        this.rendering = other.rendering;
        this.observations = other.observations;
        this.expressionsApplicationContext = other.expressionsApplicationContext;
        this.publisher = other.publisher;
        this.convertersConfigurer = other.convertersConfigurer;
        this.restClientCustomizers.addAll(other.restClientCustomizers);
        this.initializers.addAll(other.initializers);
        this.interceptors.addAll(other.interceptors);
        this.responseErrorHandlers.addAll(other.responseErrorHandlers);
        this.serviceProxyCustomizers.addAll(other.serviceProxyCustomizers);
        this.argumentResolvers.addAll(other.argumentResolvers);
        this.loggingOverride = other.loggingOverride;
        this.loggingOverrides.putAll(other.loggingOverrides);
        this.expressionVars.putAll(other.expressionVars);
        this.requestValuesTransformers.addAll(other.requestValuesTransformers);
    }

    /// @return this builder, seen as a prototype to derive other builders from
    public UpstreamPrototype<T> prototype() {
        return this;
    }

    /// @return a new builder holding a copy of this configuration, see [#UpstreamBuilder(UpstreamBuilder)]
    @Override
    public UpstreamBuilder<T> builder() {
        return new UpstreamBuilder<>(this);
    }

    /// Creates a builder whose interface is configured later with [#type].
    ///
    /// @return the builder
    public static UpstreamBuilder<?> create() {
        return new UpstreamBuilder<>();
    }

    /// Creates a builder for an interface.
    ///
    /// @param <T> the interface type
    /// @param klass the interface to implement
    /// @return the builder
    public static <T> UpstreamBuilder<T> create(Class<T> klass) {
        return create().type(klass);
    }

    /// Creates a builder for an interface, naming the upstream regardless of its [Upstream] annotation:
    /// the way to build two differently named clients of the same interface.
    ///
    /// @param <T> the interface type
    /// @param klass the interface to implement
    /// @param name the upstream name
    /// @return the builder
    public static <T> UpstreamBuilder<T> named(Class<T> klass, String name) {
        return create().type(klass).name(name);
    }

    /// Configures the interface to implement.
    ///
    /// @param <K> the interface type
    /// @param klass the interface to implement
    /// @return this builder, retyped
    @SuppressWarnings("unchecked")
    public <K> UpstreamBuilder<K> type(Class<K> klass) {
        this.klass = klass;
        return (UpstreamBuilder<K>) this;
    }

    /// Names the upstream, overriding its [Upstream] annotation.
    ///
    /// @param name the name, or `null` to use the annotation; a blank name makes the client use the
    /// interface simple name, without looking at the annotation
    /// @return this builder
    public UpstreamBuilder<T> name(@Nullable String name) {
        this.name = Optional.ofNullable(name);
        return this;
    }

    /// Configures an upstream aware request factory when the condition holds, see
    /// [#requestFactory(UpstreamHttpRequestFactory)].
    ///
    /// @param test whether to configure the factory
    /// @param factory the factory
    /// @return this builder
    /// @throws IllegalArgumentException when the condition holds and a request factory is already
    /// configured
    public UpstreamBuilder<T> requestFactoryIf(boolean test, UpstreamHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    /// Configures a request factory that creates its requests knowing the invocation at hand. It is
    /// preprocessed when the client is built.
    ///
    /// The factory is used as is: no buffering is added, so whether the response bodies can be read by
    /// the logs, the alerts and the error conditions as well as by the mapping depends on its responses.
    ///
    /// @param factory the factory
    /// @return this builder
    /// @throws IllegalArgumentException when a request factory is already configured
    public UpstreamBuilder<T> requestFactory(UpstreamHttpRequestFactory factory) {
        Assert.isNull(this.rfp, "request factory is already configured");
        this.rfp = (ScopeHandler sh, Class<?> klass1, Expressions exprs, Map<Method, EndpointDescriptor> eps) -> {
            factory.preprocess(klass1, exprs, eps);
            return sh.adapt(factory);
        };
        return this;
    }

    /// Configures a spring request factory when the condition holds, see
    /// [#requestFactory(ClientHttpRequestFactory)].
    ///
    /// @param test whether to configure the factory
    /// @param factory the factory
    /// @return this builder
    /// @throws IllegalArgumentException when the condition holds and a request factory is already
    /// configured
    public UpstreamBuilder<T> requestFactoryIf(boolean test, ClientHttpRequestFactory factory) {
        if (!test) {
            return this;
        }
        return requestFactory(factory);
    }

    /// Configures a spring request factory, used as is: no buffering is added, so whether the response
    /// bodies can be read by the logs, the alerts and the error conditions as well as by the mapping
    /// depends on its responses (spring's `BufferingClientHttpRequestFactory` makes them re-readable).
    ///
    /// @param factory the factory
    /// @return this builder
    /// @throws IllegalArgumentException when a request factory is already configured
    public UpstreamBuilder<T> requestFactory(ClientHttpRequestFactory factory) {
        Assert.isNull(this.rfp, "request factory is already configured");
        this.rfp = (ScopeHandler sh, Class<?> klass1, Expressions exprs, Map<Method, EndpointDescriptor> eps) -> {
            return factory;
        };
        return this;
    }

    /// Configures the mock request factory when the condition holds, see [#requestFactoryMock].
    ///
    /// @param test whether to configure the factory
    /// @param customizer customizes the mocks, invoked only when the condition holds
    /// @return this builder
    /// @throws IllegalArgumentException when the condition holds and a request factory is already
    /// configured
    public UpstreamBuilder<T> requestFactoryMockIf(boolean test, Consumer<MocksCustomizer> customizer) {
        if (!test) {
            return this;
        }
        return requestFactoryMock(customizer);
    }

    /// Configures a request factory that sends nothing and answers with mocked responses: the ones of
    /// the response factory set by the customizer if any, the `@Upstream.Mock` resources of the endpoint
    /// otherwise. Everything else (initializers, interceptors, logging, alerts, error handling, mapping)
    /// runs as for a real exchange.
    ///
    /// Responses are buffered, except for the endpoints returning a stream (see
    /// [net.optionfactory.spring.upstream.buffering.Buffering]).
    ///
    /// @param customizer customizes the mocks, invoked immediately
    /// @return this builder
    /// @throws IllegalArgumentException when the customizer is `null` or a request factory is already
    /// configured
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

    /// Configures the Apache HttpComponents request factory when the condition holds, see
    /// [#requestFactoryHttpComponents].
    ///
    /// @param test whether to configure the factory
    /// @param customizer customizes the factory, invoked only when the condition holds
    /// @return this builder
    /// @throws IllegalArgumentException when the condition holds and a request factory is already
    /// configured
    public UpstreamBuilder<T> requestFactoryHttpComponentsIf(boolean test, Consumer<HcRequestFactories.Builder> customizer) {
        if (!test) {
            return this;
        }
        return requestFactoryHttpComponents(customizer);
    }

    /// Configures a request factory backed by a pooled Apache HttpComponents 5 client, configured by the
    /// `@Upstream.HttpComponents` annotation of the interface and then by the customizer, whose settings
    /// win. The client is created when this client is built.
    ///
    /// Responses are buffered, except for the endpoints returning a stream (see
    /// [net.optionfactory.spring.upstream.buffering.Buffering]).
    ///
    /// @param customizer customizes the factory, invoked immediately
    /// @return this builder
    /// @throws IllegalArgumentException when the customizer is `null` or a request factory is already
    /// configured
    public UpstreamBuilder<T> requestFactoryHttpComponents(Consumer<HcRequestFactories.Builder> customizer) {
        Assert.notNull(customizer, "customizer must not be null");
        Assert.isNull(this.rfp, "request factory is already configured");
        final var builder = HcRequestFactories.builder();
        customizer.accept(builder);
        this.rfp = builder.buildConfigurer(Buffering.BUFFERED);
        return this;
    }

    /// Configures the logging of one endpoint, winning over [#logging(Upstream.Logging.Conf)] and over
    /// the `@Upstream.Logging` annotations.
    ///
    /// @param m the endpoint method
    /// @param c the configuration
    /// @return this builder
    public UpstreamBuilder<T> logging(Method m, Upstream.Logging.Conf c) {
        loggingOverrides.put(m, c);
        return this;
    }

    /// Configures the logging of every endpoint, winning over the `@Upstream.Logging` annotations: the
    /// endpoints without annotations are logged too.
    ///
    /// @param c the configuration, or `null` to go back to the annotations
    /// @return this builder
    public UpstreamBuilder<T> logging(Upstream.Logging.Conf c) {
        loggingOverride = Optional.ofNullable(c);
        return this;
    }

    /// Configures the message converters of the client, replacing whatever an earlier call, or [#json],
    /// [#xml] or [#soap], configured: the last of these calls wins.
    ///
    /// The configurer receives a builder with no converters registered, and the client has no other
    /// ones: without a configurer, no body can be read or written. The resulting converters also serve
    /// the error conditions (`#json_path`) and
    /// [net.optionfactory.spring.upstream.errors.RestClientUpstreamException#getResponseBodyAs(Class)].
    ///
    /// @param configurer registers the converters, or `null` for none
    /// @return this builder
    public UpstreamBuilder<T> messageConverters(Consumer<HttpMessageConverters.ClientBuilder> configurer) {
        this.convertersConfigurer = Optional.ofNullable(configurer);
        return this;
    }

    /// Configures a SOAP client whose JAXB context covers the packages of the given classes, see
    /// [#soap(Protocol, Schema, SoapHeaderWriter, JAXBContext)].
    ///
    /// @param protocol the SOAP version spoken
    /// @param schema validates the bodies, or `null` for no validation
    /// @param headerWriter writes the SOAP header of each request, or `null` for an empty header
    /// @param clazz a class of the first package to bind, typically one generated by xjc
    /// @param more classes of further packages to bind
    /// @return this builder
    /// @throws IllegalStateException when the JAXB context cannot be created
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

    /// Configures a SOAP client: registers the initializer sending the `@Upstream.SoapAction` of each
    /// endpoint, and replaces the message converters (see [#messageConverters]) with the SOAP envelope
    /// ones.
    ///
    /// @param protocol the SOAP version spoken
    /// @param schema validates the bodies, or `null` for no validation
    /// @param headerWriter writes the SOAP header of each request, or `null` for an empty header
    /// @param context the JAXB context of the request and response classes
    /// @return this builder
    public UpstreamBuilder<T> soap(Protocol protocol, @Nullable Schema schema, @Nullable SoapHeaderWriter headerWriter, JAXBContext context) {
        this.initializer(new UpstreamSoapActionInitializer(protocol));
        this.messageConverters(c -> {
            c.addCustomConverter(new SoapMessageHttpMessageConverter(protocol));
            c.addCustomConverter(new SoapJaxbHttpMessageConverter(protocol, context, schema, headerWriter));
        });
        return this;
    }

    /// Configures a JSON/HTTP client, replacing the message converters (see [#messageConverters]).
    ///
    /// Besides JSON through the given mapper, the client handles `byte[]`, `String`, `Resource`,
    /// `InputStream` and `Stream` (JSON arrays or JSON lines, read lazily) bodies, and multipart forms
    /// whose parts can be JSON.
    ///
    /// @param jsonMapper the mapper
    /// @return this builder
    public UpstreamBuilder<T> json(JsonMapper jsonMapper) {
        this.messageConverters(c -> {
            final var multipart = new FormHttpMessageConverter();
            multipart.addPartConverter(new JacksonJsonHttpMessageConverter(jsonMapper));
            c.addCustomConverter(new ByteArrayHttpMessageConverter());
            c.addCustomConverter(new StringHttpMessageConverter());
            c.addCustomConverter(new ResourceHttpMessageConverter(false));
            c.addCustomConverter(new InputStreamHttpMessageConverter());
            c.addCustomConverter(StreamHttpMessageConverter.forJson(jsonMapper));
            c.addCustomConverter(multipart);
            c.addCustomConverter(new JacksonJsonHttpMessageConverter(jsonMapper));
        });
        return this;
    }

    /// Configures an XML/HTTP client, replacing the message converters (see [#messageConverters]).
    ///
    /// Besides XML through the given mapper, the client handles `byte[]`, `String`, `Resource`,
    /// `InputStream` and `Stream` (repeated elements, read lazily) bodies, and multipart forms whose parts
    /// can be XML.
    ///
    /// @param xmlMapper the mapper
    /// @return this builder
    public UpstreamBuilder<T> xml(XmlMapper xmlMapper) {
        this.messageConverters(c -> {
            final var multipart = new FormHttpMessageConverter();
            multipart.addPartConverter(new JacksonXmlHttpMessageConverter(xmlMapper));
            c.addCustomConverter(new ByteArrayHttpMessageConverter());
            c.addCustomConverter(new StringHttpMessageConverter());
            c.addCustomConverter(new ResourceHttpMessageConverter(false));
            c.addCustomConverter(new InputStreamHttpMessageConverter());
            c.addCustomConverter(StreamHttpMessageConverter.forXml(xmlMapper));
            c.addCustomConverter(multipart);
            c.addCustomConverter(new JacksonXmlHttpMessageConverter(xmlMapper));
        });
        return this;
    }

    /// Customizes the underlying `RestClient`, after the request factory and the converters are set.
    /// Customizers run in registration order, [#baseUri] being one of them.
    ///
    /// @param customizer the customizer
    /// @return this builder
    public UpstreamBuilder<T> restClient(Consumer<RestClient.Builder> customizer) {
        restClientCustomizers.add(customizer);
        return this;
    }

    /// Binds a variable visible as `#key` to every expression of the client, annotation attributes
    /// included. A method argument with the same name hides it.
    ///
    /// @param key the variable name
    /// @param value the value
    /// @return this builder
    public UpstreamBuilder<T> var(String key, Object value) {
        expressionVars.put(key, value);
        return this;
    }

    /// Configures the uri the `@HttpExchange` urls are resolved against.
    ///
    /// @param baseUri the base uri
    /// @return this builder
    public UpstreamBuilder<T> baseUri(URI baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri.toString()));
        return this;
    }

    /// Configures the uri the `@HttpExchange` urls are resolved against.
    ///
    /// @param baseUri the base uri
    /// @return this builder
    public UpstreamBuilder<T> baseUri(String baseUri) {
        restClientCustomizers.add(b -> b.baseUrl(baseUri));
        return this;
    }

    /// Adds a request initializer when the condition holds, see [#initializer].
    ///
    /// @param test whether to add the initializer
    /// @param initializer the initializer
    /// @return this builder
    public UpstreamBuilder<T> initializerIf(boolean test, UpstreamHttpRequestInitializer initializer) {
        if (!test) {
            return this;
        }
        return initializer(initializer);
    }

    /// Adds a request initializer, run in registration order before the interceptors; it is
    /// preprocessed when the client is built.
    ///
    /// @param initializer the initializer
    /// @return this builder
    public UpstreamBuilder<T> initializer(UpstreamHttpRequestInitializer initializer) {
        this.initializers.add(initializer);
        return this;
    }

    /// Adds an interceptor when the condition holds, see [#interceptor].
    ///
    /// @param test whether to add the interceptor
    /// @param interceptor the interceptor
    /// @return this builder
    public UpstreamBuilder<T> interceptorIf(boolean test, UpstreamHttpInterceptor interceptor) {
        if (!test) {
            return this;
        }
        return interceptor(interceptor);
    }

    /// Adds an interceptor, run in registration order around the built-in ones; it is preprocessed when
    /// the client is built.
    ///
    /// @param interceptor the interceptor
    /// @return this builder
    public UpstreamBuilder<T> interceptor(UpstreamHttpInterceptor interceptor) {
        interceptors.add(interceptor);
        return this;
    }

    /// Customizes, in place, the list of transformers applied to the request values of each invocation,
    /// before the request is created; the transformer of `@Upstream.PathVariable` always runs first.
    ///
    /// @param customizer receives the registered transformers, and can add, remove or reorder them
    /// @return this builder
    public UpstreamBuilder<T> requestValuesTransformers(Consumer<List<HttpRequestValuesTransformer>> customizer) {
        customizer.accept(requestValuesTransformers);
        return this;
    }

    /// Adds a transformer of the request values of each invocation, see [#requestValuesTransformers].
    ///
    /// @param rvt the transformer
    /// @return this builder
    public UpstreamBuilder<T> requestValuesTransformer(HttpRequestValuesTransformer rvt) {
        this.requestValuesTransformers.add(rvt);
        return this;
    }

    /// Adds a response error handler, consulted in registration order before the built-in ones, see
    /// [UpstreamResponseErrorHandler].
    ///
    /// @param eh the handler
    /// @return this builder
    public UpstreamBuilder<T> responseErrorHandler(UpstreamResponseErrorHandler eh) {
        responseErrorHandlers.add(eh);
        return this;
    }

    /// Customizes the `HttpServiceProxyFactory` implementing the interface, e.g. to register a
    /// conversion service; runs before the argument resolvers of [#argumentResolver] are added.
    ///
    /// @param customizer the customizer
    /// @return this builder
    public UpstreamBuilder<T> serviceProxy(Consumer<HttpServiceProxyFactory.Builder> customizer) {
        serviceProxyCustomizers.add(customizer);
        return this;
    }

    /// Registers a resolver for the method arguments the `HttpServiceProxyFactory` does not handle out of
    /// the box. The resolver of the `@Upstream.Context` and `@Upstream.Principal` arguments is always
    /// registered first.
    ///
    /// @param argResolver the resolver
    /// @return this builder
    public UpstreamBuilder<T> argumentResolver(HttpServiceArgumentResolver argResolver) {
        this.argumentResolvers.add(argResolver);
        return this;
    }

    /// Configures the clock timing the requests and the responses, as seen in their contexts and in the
    /// logged elapsed times.
    ///
    /// @param clock the clock, or `null` for `InstantSource.system()`
    /// @return this builder
    public UpstreamBuilder<T> clock(@Nullable InstantSource clock) {
        this.clock = clock;
        return this;
    }

    /// Configures where the principal of an invocation comes from when the method has no
    /// `@Upstream.Principal` argument, or when that argument is `null`. The supplier is called on the
    /// invoking thread, at most once per invocation.
    ///
    /// @param principal the supplier, possibly returning `null` for no principal; `null` to rely on the
    /// annotated arguments only
    /// @return this builder
    public UpstreamBuilder<T> principal(@Nullable Supplier<Object> principal) {
        this.principal = principal;
        return this;
    }

    /// Creates the request factory of a client when it is built, knowing its interface and endpoints:
    /// what the `requestFactory*` methods configure.
    public static interface RequestFactoryProvider {

        /// @param sh adapts upstream aware components to their spring counterparts
        /// @param klass the interface being implemented
        /// @param expressions the expressions of the client
        /// @param endpoints the endpoints of the client, by method
        /// @return the request factory of the client
        ClientHttpRequestFactory configure(ScopeHandler sh, Class<?> klass, Expressions expressions, Map<Method, EndpointDescriptor> endpoints);
    }

    /// Configures the registry of the `upstream` observation recorded for each invocation, tagged with
    /// the `upstream` and `endpoint` names and with the `alert` raised, if any.
    ///
    /// @param observations the registry, or `null` for no observations
    /// @return this builder
    public UpstreamBuilder<T> observations(ObservationRegistry observations) {
        this.observations = observations;
        return this;
    }

    /// Configures the application context whose beans the expressions can reference, as `@beanName`, and
    /// whose bean factory serves the conversions.
    ///
    /// @param ac the application context, or `null` for expressions without beans
    /// @return this builder
    public UpstreamBuilder<T> expressions(@Nullable ConfigurableApplicationContext ac) {
        this.expressionsApplicationContext = ac;
        return this;
    }

    /// Configures how payloads are rendered in logs and alerts, replacing the default rendering, which
    /// redacts nothing.
    ///
    /// @param customizer configures the redactions, invoked immediately
    /// @return this builder
    public UpstreamBuilder<T> redact(Consumer<PayloadsRendering.Configurer> customizer) {
        final var builder = PayloadsRendering.builder();
        customizer.accept(builder);
        this.rendering = builder.build();
        return this;
    }

    /// Configures where the [net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent]s are
    /// published; without a publisher, alerts are only recorded in the observations.
    ///
    /// @param publisher the publisher, or `null` to discard the events
    /// @return this builder
    public UpstreamBuilder<T> publisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        return this;
    }

    /// Configures an application context both as the [#expressions] context and as the alert
    /// [#publisher].
    ///
    /// @param ac the application context, or `null` for neither
    /// @return this builder
    public UpstreamBuilder<T> applicationContext(@Nullable ConfigurableApplicationContext ac) {
        this.expressionsApplicationContext = ac;
        this.publisher = ac;
        return this;
    }

    /// Builds the client.
    ///
    /// The annotations are read and their expressions parsed now, so that a malformed expression or an
    /// unparseable `@Upstream.HttpComponents` timeout fails here rather than on the first call. Default
    /// methods of the interface are invoked as such, and can call the other methods.
    ///
    /// @return the client, safe to use from multiple threads
    /// @throws IllegalArgumentException when no interface or no request factory is configured
    public T build() {
        Assert.notNull(klass, "type must be configured");
        final var upstreamId = name.or(() -> Annotations.closest(klass, Upstream.class)
                .map(a -> a.value()))
                .filter(n -> !n.isBlank())
                .orElse(klass.getSimpleName());

        final var endpoints = Stream.of(klass.getMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge() && !m.isDefault())
                .filter(m -> AnnotationUtils.findAnnotation(m, HttpExchange.class) != null)
                .map(m -> {
                    final var epa = AnnotationUtils.findAnnotation(m, Upstream.Endpoint.class);
                    final var principalIndex = IntStream
                            .range(0, m.getParameters().length)
                            .filter(i -> m.getParameters()[i].isAnnotationPresent(Upstream.Principal.class) || m.getParameters()[i].getType().isAnnotationPresent(Upstream.Principal.class))
                            .mapToObj(i -> i)
                            .findFirst();
                    return new EndpointDescriptor(upstreamId, epa == null ? m.getName() : epa.value(), m, principalIndex.orElse(null));
                })
                .collect(Collectors.toMap(EndpointDescriptor::method, ed -> ed));

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

        final var renderingOrDefault = rendering != null ? rendering : PayloadsRendering.builder().build();
        final var scopeHandler = new ThreadLocalScopeHandler(principalOrDefault, clockOrDefault, endpoints, expressions, renderingOrDefault, obs, pub);

        final var requestFactory = this.rfp.configure(scopeHandler, klass, expressions, endpoints);

        final var httpMessageConverterBuilder = HttpMessageConverters.forClient();
        convertersConfigurer.ifPresent(c -> c.accept(httpMessageConverterBuilder));
        final var httpMessageConverters = httpMessageConverterBuilder.build();

        final var rcb = RestClient.builder().requestFactory(requestFactory).configureMessageConverters(configurer -> {
            httpMessageConverters.forEach(configurer::addCustomConverter);
        });
        restClientCustomizers.forEach(c -> c.accept(rcb));

        initializers.stream()
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .map(scopeHandler::adapt)
                .forEach(rcb::requestInitializer);

        final var initializedInterceptors = Stream.concat(interceptors.stream(),
                Stream.of(new UpstreamAnnotatedHeadersInterceptor(),
                        new UpstreamAnnotatedCookiesInterceptor(),
                        new UpstreamAnnotatedQueryParamsInterceptor(),
                        new UpstreamLoggingInterceptor(loggingOverride, loggingOverrides),
                        new UpstreamAlertInterceptor(pub, obs)
                ))
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .toList();

        rcb.requestInterceptor(scopeHandler.adapt(initializedInterceptors));

        Stream.concat(responseErrorHandlers.stream(), Stream.of(new UpstreamErrorOnErrorStatusHandler(), new UpstreamErrorOnResponseHandler()))
                .peek(i -> i.preprocess(klass, expressions, endpoints))
                .map(scopeHandler::adapt)
                .forEach(rcb::defaultStatusHandler);

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
        p.addAdvice(scopeHandler.interceptor(new MessageConverters(httpMessageConverters)));
        @SuppressWarnings("unchecked")
        final var r = (T) p.getProxy();
        return r;
    }

    /// Claims the `@Upstream.Context` and `@Upstream.Principal` arguments (or those whose type carries
    /// the annotation), so that the `HttpServiceProxyFactory` neither sends them nor rejects them.
    public static class ContextArgumentResolver implements HttpServiceArgumentResolver {

        /// @param argument the argument value
        /// @param parameter the parameter
        /// @param requestValues the request values, left untouched
        /// @return true for the context and principal parameters, false otherwise
        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            return parameter.hasParameterAnnotation(Context.class)
                    || parameter.getParameterType().isAnnotationPresent(Context.class)
                    || parameter.hasParameterAnnotation(Principal.class)
                    || parameter.getParameterType().isAnnotationPresent(Principal.class);
        }

    }

}
