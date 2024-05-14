package net.optionfactory.spring.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.upstream.caching.FetchMode;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import org.springframework.core.MethodParameter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Upstream {

    String value() default "";

    int connectionTimeout() default 5;

    int socketTimeout() default 30;

    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.PARAMETER)
    public @interface Principal {

    }

    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    public @interface Endpoint {

        String value();

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface Logging {

        Strategy request() default Strategy.ABBREVIATED_COMPACT;

        int requestMaxSize() default 8 * 1024;

        boolean requestHeaders() default false;

        Strategy response() default Strategy.ABBREVIATED_COMPACT;

        int responseMaxSize() default 8 * 1024;

        boolean responseHeaders() default false;

        String infix() default "✂️";

    }

    @Target({ElementType.METHOD})
    @Retention(value = RetentionPolicy.RUNTIME)
    @Repeatable(Mock.List.class)
    @Documented
    public @interface Mock {

        /**
         * @return the body template path, as a SpEl template expression
         */
        String value();

        HttpStatus status() default HttpStatus.OK;

        /**
         * @return Headers, as a SpEl template expressions
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

    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.PARAMETER)
    @Repeatable(Param.List.class)
    public @interface Param {

        public Type type() default Type.QUERY_PARAM;

        public String key();

        public String value();

        public String condition() default "true";

        public enum Type {
            QUERY_PARAM,
            PATH_VARIABLE,
            COOKIE,
            HEADER;
        }

        @Target({ElementType.PARAMETER})
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        public @interface List {

            Param[] value();
        }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SoapAction {

        String value();

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface FaultOnResponse {

        public static final String STATUS_IS_ERROR = "#response.status().isError()";

        String value();

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface FaultOnRemotingError {

        public static final String ALWAYS = "true";

        String value() default ALWAYS;

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    @Repeatable(ErrorOnResponse.List.class)
    public @interface ErrorOnResponse {

        String value() default "false";

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
            final var params = parameter.getParameter().getAnnotationsByType(Param.class);
            for (Param p : params) {
                final StandardEvaluationContext ec = new StandardEvaluationContext(argument);
                if (!"true".equals(p.condition()) && !parser.parseExpression(p.condition()).getValue(ec, boolean.class)) {
                    continue;
                }
                final var value = parser.parseExpression(p.value()).getValue(ec, String.class);
                switch (p.type()) {
                    case HEADER ->
                        requestValues.addHeader(p.key(), value);
                    case QUERY_PARAM ->
                        requestValues.addRequestParameter(p.key(), value);
                    case PATH_VARIABLE ->
                        requestValues.setUriVariable(p.key(), value);
                    case COOKIE ->
                        requestValues.addCookie(p.key(), value);
                }
            }
            return params.length != 0
                    || parameter.getParameterType() == FetchMode.class
                    || parameter.hasParameterAnnotation(Principal.class);

        }

    }

}
