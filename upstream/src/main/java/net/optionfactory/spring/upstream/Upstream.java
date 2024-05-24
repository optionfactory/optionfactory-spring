package net.optionfactory.spring.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.upstream.rendering.BodyRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import org.springframework.core.MethodParameter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
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

    /**
     * @return the connection timeout (in seconds)
     */
    int connectionTimeout() default 5;

    /**
     * @return the socket timeout (in seconds)
     */
    int socketTimeout() default 30;

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
         * @return the <strong>templated expressions</strong> to be evaluated as
         * the body template path
         *
         */
        String value();

        HttpStatus status() default HttpStatus.OK;

        /**
         * @return the <strong>templated expressions</strong> to be evaluated as
         * http headers
         */
        String[] headers() default {};

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
         * @return the <strong>templated expression</strong> to be evaluated
         */
        public String key();

        /**
         * @return the <strong>string expression</strong> to be evaluated
         */
        public String value();

        /**
         * @return the <strong>boolean expression</strong> to be evaluated
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
    @Repeatable(QueryParam.List.class)
    public @interface QueryParam {

        /**
         * @return the <strong>templated expression</strong> to be evaluated
         */
        public String key();

        /**
         * @return the <strong>string expression</strong> to be evaluated
         */
        public String value();

        /**
         * @return the <strong>boolean expression</strong> to be evaluated
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
         * @return the key
         */
        public String key();

        /**
         * <strong>context</strong>: #this: the annotated argument.
         *
         * @return the <strong>string expression</strong> to be evaluated
         *
         */
        public String value();

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
         * @return the <strong>templated expression</strong> to be evaluated
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
    public @interface FaultOnResponse {

        public static final String STATUS_IS_ERROR = "#response.status().isError()";

        /**
         * @return the <strong>boolean expression</strong> to be evaluated
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
         * @return the <strong>boolean expression</strong> to be evaluated
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
         * @return the <strong>boolean expression</strong> to be evaluated
         */
        String value() default "false";

        /**
         * @return the <strong>templated expression</strong> to be evaluated
         */
        String reason() default "upstream error";

        HttpStatus.Series[] series() default HttpStatus.Series.SUCCESSFUL;

        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            ErrorOnResponse[] value();
        }
    }

    public static class ArgumentResolver implements HttpServiceArgumentResolver {

        private final SpelExpressionParser parser = new SpelExpressionParser();

        @Override
        public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
            final var uvs = parameter.getParameter().getAnnotationsByType(PathVariable.class);
            for (PathVariable uv : uvs) {
                final StandardEvaluationContext ec = new StandardEvaluationContext(argument);
                final var value = parser.parseExpression(uv.value()).getValue(ec, String.class);
                requestValues.setUriVariable(uv.key(), value);
            }
            return uvs.length != 0
                    || parameter.hasParameterAnnotation(Context.class)
                    || parameter.getParameterType().isAnnotationPresent(Context.class)
                    || parameter.hasParameterAnnotation(Principal.class)
                    || parameter.getParameterType().isAnnotationPresent(Principal.class);

        }

    }

}
