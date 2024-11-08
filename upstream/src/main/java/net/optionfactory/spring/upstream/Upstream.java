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
import net.optionfactory.spring.upstream.rendering.BodyRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import org.springframework.http.HttpStatus;

/**
 *
 * <strong>discovery</strong>: interface, super interfaces<br>
 * <strong>meta</strong>: no<br>
 * <strong>merging</strong>: no<br>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Upstream {

    /**
     * @return the name of this upstream
     */
    String value() default "";

    /**
     * Apache HttpCmponents 5 specific configuration for an upstream.
     * Configuration is honored iff an HttpComponents request factory is
     * configured.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface HttpComponents {

        /**
         * @return the connection timeout, parseable by Duration.parse
         */
        String connectionTimeout() default "PT5S";

        public Type connectionTimeoutType() default Type.TEMPLATED;

        /**
         * @return the socket timeout, parseable by Duration.parse
         */
        String socketTimeout() default "PT30S";

        public Type socketTimeoutType() default Type.TEMPLATED;

        String maxConnections() default "100";

        public Type maxConnectionsType() default Type.STATIC;

        String maxConnectionsPerRoute() default "100";

        public Type maxConnectionsPerRouteType() default Type.STATIC;

        boolean disableAuthCaching() default false;

        boolean disableAutomaticRetries() default false;

        boolean disableConnectionState() default false;

        boolean disableContentCompression() default false;

        boolean disableCookieManagement() default false;

        boolean disableDefaultUserAgent() default false;

        boolean disableRedirectHandling() default false;
    }

    /**
     * Marks a parameter as the principal. The annotated parameter is ignored by
     * the HttpServiceProxy. The annotated parameter will be retrived by using
     * {@code #invocation.principal} The annotated parameter will be used by the
     * logging interceptor.
     * <strong>discovery</strong>: parameter, parameter class<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Principal {

    }

    /**
     * Mark a parameter as a context parameter. Context parameters are ignored
     * by the HttpServiceProxy but can be used by expressions and interceptors.
     *
     * <strong>discovery</strong>: parameter, parameter class<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Context {

    }

    /**
     * Used to give a descriptive name to an endpoint.
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    public @interface Endpoint {

        /**
         * @return the name of this endpoint
         */
        String value();

    }

    /**
     * <strong>discovery</strong>: method, interface, super interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface Logging {

        public static final String INFIX_SCISSORS = "✂️";
        public static final int DEFAULT_MAX_SIZE = 8 * 1024;

        Strategy request() default Strategy.ABBREVIATED_COMPACT;

        int requestMaxSize() default DEFAULT_MAX_SIZE;

        HeadersStrategy requestHeaders() default HeadersStrategy.SKIP;

        Strategy response() default Strategy.ABBREVIATED_COMPACT;

        int responseMaxSize() default DEFAULT_MAX_SIZE;

        HeadersStrategy responseHeaders() default HeadersStrategy.SKIP;

        String infix() default INFIX_SCISSORS;

        public record Conf(
                Strategy request,
                int requestMaxSize,
                HeadersStrategy requestHeaders,
                Strategy response,
                int responseMaxSize,
                HeadersStrategy responseHeaders,
                String infix) {

            public static Conf defaults() {
                return new Conf(Strategy.ABBREVIATED_COMPACT, DEFAULT_MAX_SIZE, HeadersStrategy.SKIP, Strategy.ABBREVIATED_COMPACT, DEFAULT_MAX_SIZE, HeadersStrategy.SKIP, INFIX_SCISSORS);
            }
        }

    }

    /**
     * Mock specific configuration for an upstream endpoint. Configuration is
     * honored iff a mock request factory is configured.
     *
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Target({ElementType.METHOD})
    @Retention(value = RetentionPolicy.RUNTIME)
    @Repeatable(Mock.List.class)
    @Documented
    public @interface Mock {

        /**
         * @return the body template path
         *
         */
        String value();

        Type valueType() default Type.TEMPLATED;

        HttpStatus status() default HttpStatus.OK;

        /**
         * @return the response http headers
         */
        String[] headers() default {};

        Type headersType() default Type.TEMPLATED;

        /**
         * Default Content-Type that will be produced when mocking endpoints.
         * <strong>discovery</strong>: declaring class, super interfaces<br>
         * <strong>meta</strong>: no<br>
         * <strong>merging</strong>: no<br>
         */
        @Target({ElementType.TYPE})
        @Retention(RetentionPolicy.RUNTIME)
        public @interface DefaultContentType {

            String value();

        }

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            Mock[] value();
        }
    }

    /**
     * An expression based HTTP header.
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(Header.List.class)
    public @interface Header {

        /**
         * @return the header name
         */
        public String key();

        public Type keyType() default Type.TEMPLATED;

        /**
         * @return the header value
         */
        public String value();

        public Type valueType() default Type.EXPRESSION;

        /**
         * @return the condition expression
         */
        public String condition() default "true";

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            Header[] value();
        }
    }

    /**
     * An expression based Cookie.
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(Cookie.List.class)
    public @interface Cookie {

        /**
         * @return the header value
         */
        public String value();

        public Type valueType() default Type.TEMPLATED;

        /**
         * @return the condition expression
         */
        public String condition() default "true";

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            Cookie[] value();
        }
    }

    /**
     * An expression based query parameter.
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(QueryParam.List.class)
    public @interface QueryParam {

        /**
         * @return the query param name
         */
        public String key();

        public Type keyType() default Type.TEMPLATED;

        /**
         * @return the query param value
         */
        public String value();

        public Type valueType() default Type.EXPRESSION;

        /**
         * @return the condition to be evaluated
         */
        public String condition() default "true";

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            QueryParam[] value();
        }
    }

    /**
     * An expression based path variable.
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    @Repeatable(PathVariable.List.class)
    public @interface PathVariable {

        /**
         * The path variable name.
         *
         * @return the key
         */
        public String key();

        public Type keyType() default Type.STATIC;

        /**
         * The path variable value.
         *
         * @return the value of the path variable
         *
         */
        public String value();

        public Type valueType() default Type.EXPRESSION;

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            PathVariable[] value();
        }

    }

    /**
     * Adds the evaluated expression to the request as a
     * <ul>
     * <li>{@code SOAPAction} header if the configured protocol is
     * {@code SOAP_1_1}
     * <li>{@code action} parameter to the Content-Type header if the configured
     * protocol is {@code SOAP_1_2}
     * </ul>
     * <strong>discovery</strong>: method<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SoapAction {

        /**
         * @return the soap action
         */
        String value();

        public Type valueType() default Type.TEMPLATED;

    }

    /**
     * Annotated method or any method in the annotated type will generate an
     * alert when the configured condition matches. The configured condition is
     * usually evaluated against the received response.
     *
     * @see AlertOnRemotingError for generating alert events on remoting
     * errors.<br>
     * @see InvocationContext exposed as {@code #invocation}<br>
     * @see RequestContext exposed as {@code #request}<br>
     * @see ResponseContext exposed as {@code #response}<br>
     * <strong>discovery</strong>: method, interface, super-interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface AlertOnResponse {

        public static final String STATUS_IS_ERROR = "#response.status().isError()";

        /**
         * @return the condition to be evaluated
         */
        String value();

    }

    /**
     * Annotated method or any method in the annotated type will generate an
     * alert event when a remoting error occurs.
     *
     * @see AlertOnResponse for generating alert events when a response is
     * received<br>
     * @see InvocationContext exposed as {@code #invocation}<br>
     * @see RequestContext exposed as {@code #request}<br>
     * @see ExceptionContext exposed as {@code #exception}<br>
     * <strong>discovery</strong>: method, interface, super-interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface AlertOnRemotingError {

        public static final String ALWAYS = "true";

        /**
         * @return the condition to be evaluated
         */
        String value() default ALWAYS;

    }

    /**
     * Annotated method or any method in the annotated type will generate an
     * exception when a the configured condition is matches. The configured
     * condition is usually evaluated against the received response.
     *
     * @see InvocationContext exposed as {@code #invocation}<br>
     * @see RequestContext exposed as {@code #request}<br>
     * @see ResponseContext exposed as {@code #response}<br>
     * <strong>discovery</strong>: method, interface, super-interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Repeatable(ErrorOnResponse.List.class)
    public @interface ErrorOnResponse {

        /**
         * @return the condition to be evaluated. Always of type
         * {@code Type.EXPRESSION}
         */
        String value() default "false";

        /**
         * @return the reported reason. Default type is {@code Type.TEMPLATED},
         *
         */
        String reason() default "upstream error";

        /**
         *
         * @return the expression type for the configured reason.
         */
        public Type reasonType() default Type.TEMPLATED;

        HttpStatus.Series[] series() default HttpStatus.Series.SUCCESSFUL;

        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            ErrorOnResponse[] value();
        }
    }

}
