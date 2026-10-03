package net.optionfactory.spring.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions.Type;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.BodiesStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.MultipartStrategy;
import org.springframework.http.HttpStatus;

/// Names an upstream client interface, and hosts the annotations that configure such clients.
///
/// An upstream client is a spring `@HttpExchange` interface implemented by [UpstreamBuilder]: the
/// nested annotations add logging, alerts, error detection, mocks and expression based request
/// values to its endpoints.
///
/// The name identifies the upstream in logs (`[upstream:name]`), in the `upstream` observation
/// key, in [net.optionfactory.spring.upstream.errors.RestClientUpstreamException] and as `#upstream`
/// in expressions. It is looked up on the built interface first and then on its super-interfaces,
/// breadth first; a name given to [UpstreamBuilder#name] wins over it, and the interface simple
/// name is used when both are missing or blank.
///
/// Many annotation attributes are strings evaluated according to a companion [Type] attribute:
/// `STATIC` values are taken verbatim, `TEMPLATED` ones evaluate the `#{...}` parts and keep the
/// rest, `EXPRESSION` ones are SpEL expressions as a whole. Expressions see the variables bound by
/// [net.optionfactory.spring.upstream.expressions.Expressions], the method arguments among them.
///
/// ```java
/// @Upstream("payments")
/// @Upstream.Logging
/// @Upstream.AlertOnRemotingError
/// @Upstream.AlertOnResponse(Upstream.AlertOnResponse.STATUS_IS_ERROR)
/// public interface PaymentsClient {
///
///     @GetExchange("/payments/{id}")
///     @Upstream.Endpoint("payment")
///     @Upstream.Header(key = "X-Tenant", value = "#tenant")
///     @Upstream.Mock("payment-#{#id}.json")
///     Payment payment(@PathVariable String id, @Upstream.Context String tenant);
/// }
/// ```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Upstream {

    /// @return the name of this upstream; blank to use the interface simple name
    String value() default "";

    /// Configures the Apache HttpComponents 5 client of an upstream.
    ///
    /// Honored only when the client is built with [UpstreamBuilder#requestFactoryHttpComponents],
    /// which looks the annotation up on the built interface and then on its super-interfaces,
    /// breadth first; the defaults of the attributes apply when it is missing. Whatever the
    /// customizer passed to the builder configures wins over the annotation.
    ///
    /// The values are evaluated once, when the client is built, against an expression context
    /// holding the builder variables and the application context beans but no invocation.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface HttpComponents {

        /// @return the timeout for establishing a connection, as an ISO-8601 duration
        /// (`Duration.parse`); an unparseable value fails the build
        String connectionTimeout() default "PT5S";

        /// @return how [#connectionTimeout] is evaluated
        public Type connectionTimeoutType() default Type.TEMPLATED;

        /// @return the timeout for waiting for data on an established connection, as an ISO-8601
        /// duration (`Duration.parse`); an unparseable value fails the build
        String socketTimeout() default "PT30S";

        /// @return how [#socketTimeout] is evaluated
        public Type socketTimeoutType() default Type.TEMPLATED;

        /// @return the maximum number of pooled connections, an int once evaluated; a value that does
        /// not yield an int fails the build
        String maxConnections() default "100";

        /// @return how [#maxConnections] is evaluated; `EXPRESSION` by default, as the value was always
        /// evaluated as a SpEL expression before this attribute was honored
        public Type maxConnectionsType() default Type.EXPRESSION;

        /// @return the maximum number of pooled connections per route, an int once evaluated; a value
        /// that does not yield an int fails the build
        String maxConnectionsPerRoute() default "100";

        /// @return how [#maxConnectionsPerRoute] is evaluated; `EXPRESSION` by default, as the value
        /// was always evaluated as a SpEL expression before this attribute was honored
        public Type maxConnectionsPerRouteType() default Type.EXPRESSION;

        /// @return true to call `HttpClientBuilder.disableAuthCaching()`
        boolean disableAuthCaching() default false;

        /// @return true to call `HttpClientBuilder.disableAutomaticRetries()`
        boolean disableAutomaticRetries() default false;

        /// @return true to call `HttpClientBuilder.disableConnectionState()`
        boolean disableConnectionState() default false;

        /// @return true to call `HttpClientBuilder.disableContentCompression()`
        boolean disableContentCompression() default false;

        /// @return true to call `HttpClientBuilder.disableCookieManagement()`
        boolean disableCookieManagement() default false;

        /// @return true to call `HttpClientBuilder.disableDefaultUserAgent()`
        boolean disableDefaultUserAgent() default false;

        /// @return true to call `HttpClientBuilder.disableRedirectHandling()`
        boolean disableRedirectHandling() default false;
    }

    /// Marks the parameter carrying the principal on whose behalf the call is made.
    ///
    /// The argument is not sent: it becomes [InvocationContext#principal()], available to
    /// expressions as `#invocation.principal()` and logged as `[user:...]` when its string form is
    /// not blank. When the argument is `null`, or no parameter is marked, the principal comes from
    /// the supplier given to [UpstreamBuilder#principal], if any.
    ///
    /// Applies to the parameter that is annotated, or whose type is; an endpoint with more than one
    /// such parameter fails [UpstreamBuilder#build].
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Principal {

    }

    /// Marks a parameter as context for expressions and interceptors only: the argument is not
    /// sent, but it is visible to expressions under its parameter name and in `#args`, and to
    /// interceptors in [InvocationContext#arguments()].
    ///
    /// Applies to every parameter that is annotated, or whose type is.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Context {

    }

    /// Names an endpoint, otherwise named after its method.
    ///
    /// The name identifies the endpoint in logs (`[ep:name]`), in the `endpoint` observation key, in
    /// [net.optionfactory.spring.upstream.errors.RestClientUpstreamException] and as `#endpoint` in
    /// expressions. Overloads can share a name, as the [net.optionfactory.spring.upstream.auth.OauthClient]
    /// ones do.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    public @interface Endpoint {

        /// @return the name of this endpoint
        String value();

    }

    /// Logs the exchanges of an endpoint, or of every endpoint of the annotated interface.
    ///
    /// Looked up on the method first, then on the built interface and on its super-interfaces,
    /// breadth first, skipping the ones unrelated to the interface declaring the method (see
    /// [net.optionfactory.spring.upstream.annotations.Annotations]). Endpoints without it are not
    /// logged, unless a configuration is given to [UpstreamBuilder#logging(Conf)] or
    /// [UpstreamBuilder#logging(java.lang.reflect.Method, Conf)], which win over the annotation.
    ///
    /// Each exchange logs at `INFO`, as configured, the request headers, a request line (method,
    /// uri, content type and body), the response headers, a response line (status, elapsed time,
    /// content type and body) and, on failure, the exception; multipart bodies get one line per
    /// part. Every line
    /// starts with `[boot:...][upstream:...][ep:...][req:...]` and the principal, if any.
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface Logging {

        /// The infix marking where an abbreviated body was cut.
        public static final String INFIX_SCISSORS = "✂️";
        /// The default maximum size of a rendered body.
        public static final int DEFAULT_MAX_SIZE = 8 * 1024;

        /// @return how multipart request bodies are rendered
        MultipartStrategy requestMultipart() default MultipartStrategy.RENDER_PARTS;

        /// @return whether the request headers are logged, redacted; `SKIP` omits their line
        HeadersStrategy requestHeaders() default HeadersStrategy.SKIP;

        /// @return how the request body is rendered; `SKIP` omits the whole request line, method and
        /// uri included
        BodiesStrategy requestBody() default BodiesStrategy.ABBREVIATED_REDACTED;

        /// @return the size beyond which the rendered request body is abbreviated
        int requestMaxSize() default DEFAULT_MAX_SIZE;

        /// @return how multipart response bodies are rendered
        MultipartStrategy responseMultipart() default MultipartStrategy.RENDER_PARTS;

        /// @return whether the response headers are logged; `SKIP` omits their line
        HeadersStrategy responseHeaders() default HeadersStrategy.SKIP;

        /// @return how the response body is rendered; `SKIP` omits the whole response line, status
        /// included
        BodiesStrategy responseBody() default BodiesStrategy.ABBREVIATED_REDACTED;

        /// @return the size beyond which the rendered response body is abbreviated
        int responseMaxSize() default DEFAULT_MAX_SIZE;

        /// @return the marker replacing the middle of an abbreviated body
        String infix() default INFIX_SCISSORS;

        /// The programmatic counterpart of [Logging], given to [UpstreamBuilder#logging(Conf)].
        ///
        /// @param requestMultipart see [Logging#requestMultipart()]
        /// @param requestHeaders see [Logging#requestHeaders()]
        /// @param requestBody see [Logging#requestBody()]
        /// @param requestMaxSize see [Logging#requestMaxSize()]
        /// @param responseMultipart see [Logging#responseMultipart()]
        /// @param responseHeaders see [Logging#responseHeaders()]
        /// @param responseBody see [Logging#responseBody()]
        /// @param responseMaxSize see [Logging#responseMaxSize()]
        /// @param infix see [Logging#infix()]
        public record Conf(
                MultipartStrategy requestMultipart,
                HeadersStrategy requestHeaders,
                BodiesStrategy requestBody,
                int requestMaxSize,
                MultipartStrategy responseMultipart,
                HeadersStrategy responseHeaders,
                BodiesStrategy responseBody,
                int responseMaxSize,
                String infix) {

            /// @return the configuration of a [Logging] annotation with no attributes set
            public static Conf defaults() {
                return new Conf(
                        MultipartStrategy.RENDER_PARTS,
                        HeadersStrategy.SKIP,
                        BodiesStrategy.ABBREVIATED_REDACTED,
                        DEFAULT_MAX_SIZE,
                        MultipartStrategy.RENDER_PARTS,
                        HeadersStrategy.SKIP,
                        BodiesStrategy.ABBREVIATED_REDACTED,
                        DEFAULT_MAX_SIZE,
                        INFIX_SCISSORS
                );
            }
        }

    }

    /// Declares the response served for an endpoint when the client is built with
    /// [UpstreamBuilder#requestFactoryMock] and no response factory is configured in its customizer.
    ///
    /// Read from the method only. When repeated, the annotations are tried in declaration order and
    /// the first one whose body resource exists is served, so a templated, argument specific
    /// resource can be followed by a generic fallback; the call fails with a `RestClientException`
    /// when none exists.
    ///
    /// The response headers accumulate, in order: the `Name: value` lines of the optional
    /// `<path>.headers` resource next to the body, then [#headers]; a header given twice keeps both
    /// values, the first one being the one read by `HttpHeaders.getFirst`. The [DefaultContentType]
    /// of the interface is used only when neither gives a `Content-Type`. The body resource is rendered by the first configured renderer (e.g. a
    /// `.tpl.json` or `.th.json` template) that accepts it, and served as is otherwise.
    @Target({ElementType.METHOD})
    @Retention(value = RetentionPolicy.RUNTIME)
    @Repeatable(Mock.List.class)
    @Documented
    public @interface Mock {

        /// @return the classpath path of the body resource, relative to the package of the
        /// interface whose declaration of the method carries the annotation unless it starts with
        /// `/`; evaluated per invocation, with the arguments bound
        String value();

        /// @return how [#value] is evaluated
        Type valueType() default Type.TEMPLATED;

        /// @return the status of the mocked response
        HttpStatus status() default HttpStatus.OK;

        /// @return additional response headers, as `Name: value` lines; a line without `:` fails the
        /// call
        String[] headers() default {};

        /// @return how each of [#headers] is evaluated
        Type headersType() default Type.TEMPLATED;

        /// The `Content-Type` of the mocked responses of an interface's endpoints.
        ///
        /// It is used only when the mock's `.headers` resource and its [Mock#headers] declare no
        /// `Content-Type`.
        ///
        /// Looked up on the interface declaring the mocked method and on its super-interfaces; a
        /// blank value is ignored.
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        public @interface DefaultContentType {

            /// @return the media type, as parsed by `MediaType.parseMediaType`
            String value();

        }

        /// Container of repeated [Mock] annotations.
        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            Mock[] value();
        }
    }

    /// Adds a request header computed per request.
    ///
    /// Read from the method only; repeatable. The values are evaluated with `#invocation`,
    /// `#request` and the arguments bound, and added to the headers already there (e.g. those set
    /// by initializers) instead of replacing them, before the application's interceptors run.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(Header.List.class)
    public @interface Header {

        /// @return the header name
        public String key();

        /// @return how [#key] is evaluated
        public Type keyType() default Type.TEMPLATED;

        /// @return the header value
        public String value();

        /// @return how [#value] is evaluated
        public Type valueType() default Type.EXPRESSION;

        /// @return a SpEL expression: the header is added only when it evaluates to true
        public String condition() default "true";

        /// Container of repeated [Header] annotations.
        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            Header[] value();
        }
    }

    /// Adds a request cookie computed per request.
    ///
    /// Read from the method only; repeatable. Each cookie is added as a `Cookie` header of its own,
    /// evaluated with `#invocation`, `#request` and the arguments bound, before the application's
    /// interceptors run.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(Cookie.List.class)
    public @interface Cookie {

        /// @return the whole cookie, as `name=value`
        public String value();

        /// @return how [#value] is evaluated
        public Type valueType() default Type.TEMPLATED;

        /// @return a SpEL expression: the cookie is added only when it evaluates to true
        public String condition() default "true";

        /// Container of repeated [Cookie] annotations.
        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            Cookie[] value();
        }
    }

    /// Appends a query parameter computed per request.
    ///
    /// Read from the method only; repeatable. Key and value are evaluated with `#invocation`,
    /// `#request` and the arguments bound, encoded once, and appended to the query the uri already
    /// has, before the application's interceptors run.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(QueryParam.List.class)
    public @interface QueryParam {

        /// @return the query param name
        public String key();

        /// @return how [#key] is evaluated
        public Type keyType() default Type.TEMPLATED;

        /// @return the query param value
        public String value();

        /// @return how [#value] is evaluated
        public Type valueType() default Type.EXPRESSION;

        /// @return a SpEL expression: the parameter is added only when it evaluates to true
        public String condition() default "true";

        /// Container of repeated [QueryParam] annotations.
        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            QueryParam[] value();
        }
    }

    /// Binds a variable of the `@HttpExchange` uri template, computed per invocation.
    ///
    /// Read from the method only; repeatable. Evaluated with `#invocation` and the arguments bound
    /// (there is no request yet), it replaces a variable of the same name bound by a
    /// `@PathVariable` argument.
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(PathVariable.List.class)
    public @interface PathVariable {

        /// @return the name of the uri template variable
        public String key();

        /// @return how [#key] is evaluated
        public Type keyType() default Type.STATIC;

        /// @return the value of the uri template variable
        public String value();

        /// @return how [#value] is evaluated
        public Type valueType() default Type.EXPRESSION;

        /// Container of repeated [PathVariable] annotations.
        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            PathVariable[] value();
        }

    }

    /// Declares the SOAP action of an endpoint of a client configured with [UpstreamBuilder#soap].
    ///
    /// Read from the method only and evaluated per invocation, the action is sent as the
    /// `SOAPAction` header for `SOAP_1_1`, and as the `action` parameter of the `Content-Type` for
    /// `SOAP_1_2`.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SoapAction {

        /// @return the soap action
        String value();

        /// @return how [#value] is evaluated
        public Type valueType() default Type.TEMPLATED;

    }

    /// Raises an alert when a response matches a condition, see [AlertOnRemotingError] for the
    /// alerts on failed exchanges.
    ///
    /// Looked up on the method first, then on the built interface and on its super-interfaces,
    /// breadth first, skipping the ones unrelated to the interface declaring the method (see
    /// [net.optionfactory.spring.upstream.annotations.Annotations]). A matching response is published
    /// as an [net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent] to the publisher given to
    /// [UpstreamBuilder#publisher], the current observation is tagged `alert=response`, and the
    /// call then proceeds as usual: the alert does not fail it, and neither does a condition that
    /// fails to evaluate or a publisher that throws, which are logged at `ERROR` instead.
    ///
    /// The condition is evaluated with [InvocationContext] as `#invocation`, [RequestContext] as
    /// `#request`, [ResponseContext] as `#response`, the arguments, and the `#json_path(path)` and
    /// `#xpath_bool(path)` functions over the response body.
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface AlertOnResponse {

        /// A condition matching the `4xx` and `5xx` responses.
        public static final String STATUS_IS_ERROR = "#response.status().isError()";

        /// @return a SpEL expression: an alert is raised when it evaluates to true
        String value();

    }

    /// Raises an alert when an exchange fails, see [AlertOnResponse] for the alerts on responses.
    ///
    /// Looked up on the method first, then on the built interface and on its super-interfaces,
    /// breadth first, skipping the ones unrelated to the interface declaring the method (see
    /// [net.optionfactory.spring.upstream.annotations.Annotations]). A matching failure is published
    /// as an [net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent], the current observation is
    /// tagged `alert=remoting`, and the exception is rethrown; a condition that fails to evaluate,
    /// or a publisher that throws, is logged at `ERROR` and does not replace it.
    ///
    /// The condition is evaluated with [InvocationContext] as `#invocation`, [RequestContext] as
    /// `#request`, [ExceptionContext] as `#exception`, and the arguments.
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface AlertOnRemotingError {

        /// A condition matching every failure.
        public static final String ALWAYS = "true";

        /// @return a SpEL expression: an alert is raised when it evaluates to true
        String value() default ALWAYS;

    }

    /// Turns a response matching a condition into a
    /// [net.optionfactory.spring.upstream.errors.RestClientUpstreamException], typically for the
    /// upstreams that report failures in a successful response.
    ///
    /// The annotations on the method, or else the ones on the nearest of the built interface and its
    /// super-interfaces (breadth first, skipping the ones unrelated to the interface declaring the
    /// method), are tried in declaration order: the first one whose [#series] contains the response
    /// status and whose condition matches provides the reason. It can be repeated, on methods and on
    /// interfaces.
    ///
    /// Listing `CLIENT_ERROR` or `SERVER_ERROR` in [#series] lets a matching annotation provide the
    /// reason of a `4xx` or `5xx` response; the ones no annotation matches still fail with the status
    /// as reason (e.g. `404 Not Found`). Non-standard status codes match no series.
    ///
    /// The condition and the reason are evaluated with [InvocationContext] as `#invocation`,
    /// [RequestContext] as `#request`, [ResponseContext] as `#response`, the arguments, and the
    /// `#json_path(path)` and `#xpath_bool(path)` functions over the response body.
    ///
    /// ```java
    /// @GetExchange("/orders")
    /// @Upstream.ErrorOnResponse(value = "#json_path('success').asBoolean() == false", reason = "rejected: #{#json_path('message').asString()}")
    /// Orders orders();
    /// ```
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Repeatable(ErrorOnResponse.List.class)
    public @interface ErrorOnResponse {

        /// @return a SpEL expression: the response is an error when it evaluates to true
        String value() default "false";

        /// @return the reason reported by the exception
        String reason() default "upstream error";

        /// @return how [#reason] is evaluated
        public Type reasonType() default Type.TEMPLATED;

        /// @return the status series the annotation applies to
        HttpStatus.Series[] series() default HttpStatus.Series.SUCCESSFUL;

        /// Container of repeated [ErrorOnResponse] annotations.
        @Target({ElementType.METHOD, ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            /// @return the repeated annotations
            ErrorOnResponse[] value();
        }
    }

}
