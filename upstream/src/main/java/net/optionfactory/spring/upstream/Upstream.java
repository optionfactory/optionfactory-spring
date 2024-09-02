package net.optionfactory.spring.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.expressions.Expressions.Type;
import net.optionfactory.spring.upstream.rendering.BodyRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

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
     * <strong>discovery</strong>: parameter, parameter class<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Principal {

    }

    /**
     * <strong>discovery</strong>: parameter, parameter class<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    public @interface Context {

    }

    /**
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

        public Type valueType() default Type.TEMPLATED;

        HttpStatus status() default HttpStatus.OK;

        /**
         * @return the response http headers
         */
        String[] headers() default {};

        public Type headersType() default Type.TEMPLATED;

        /**
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
     * <strong>discovery</strong>: parameter<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.PARAMETER)
    @Repeatable(PathVariable.List.class)
    public @interface PathVariable {

        /**
         * <strong>context</strong>: the annotated argument with method
         * parameter name.
         *
         * @return the key
         */
        public String key();

        public Type keyType() default Type.STATIC;

        /**
         * <strong>context</strong>: the annotated argument with method
         * parameter name.
         *
         * @return the value of the path variable
         *
         */
        public String value();

        public Type valueType() default Type.EXPRESSION;

        @Target({ElementType.PARAMETER})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            PathVariable[] value();
        }

    }

    /**
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
     * <strong>discovery</strong>: method, interface, super-interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface FaultOnResponse {

        public static final String STATUS_IS_ERROR = "#response.status().isError()";

        /**
         * @return the condition to be evaluated
         */
        String value();

    }

    /**
     * <strong>discovery</strong>: method, interface, super-interfaces<br>
     * <strong>meta</strong>: no<br>
     * <strong>merging</strong>: no<br>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    public @interface FaultOnRemotingError {

        public static final String ALWAYS = "true";

        /**
         * @return the condition to be evaluated
         */
        String value() default ALWAYS;

    }

    /**
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
         * @return the condition to be evaluated
         */
        String value() default "false";

        /**
         * @return the reported reason
         */
        String reason() default "upstream error";

        public Type reasonType() default Type.TEMPLATED;

        HttpStatus.Series[] series() default HttpStatus.Series.SUCCESSFUL;

        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            ErrorOnResponse[] value();
        }
    }

    public static class ArgumentResolver implements HttpServiceArgumentResolver {

        private final Expressions expressions;

        public ArgumentResolver(Expressions expressions) {
            this.expressions = expressions;
        }

        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            final var uvs = parameter.getParameter().getAnnotationsByType(PathVariable.class);
            for (PathVariable uv : uvs) {
                final var ctx = expressions.context();
                ctx.setVariable(parameter.getParameterName(), argument);
                final var key = expressions.string(uv.key(), uv.keyType()).evaluate(ctx);
                final var value = expressions.string(uv.value(), uv.valueType()).evaluate(ctx);
                requestValues.setUriVariable(key, value);
            }
            return uvs.length != 0
                    || parameter.hasParameterAnnotation(Context.class)
                    || parameter.getParameterType().isAnnotationPresent(Context.class)
                    || parameter.hasParameterAnnotation(Principal.class)
                    || parameter.getParameterType().isAnnotationPresent(Principal.class);

        }

    }

}
