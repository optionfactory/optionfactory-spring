package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import net.optionfactory.spring.upstream.rendering.BodyRendering.Strategy;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface UpstreamLogging {

    Strategy value() default Strategy.ABBREVIATED_COMPACT;

    int maxSize() default 8 * 1024;

    String infix() default "✂️";

    boolean headers() default true;

    public static class Interceptor implements UpstreamHttpInterceptor {

        private final Logger logger = LoggerFactory.getLogger(Interceptor.class);
        private final Map<Method, UpstreamLogging> confs = new ConcurrentHashMap<>();

        @Override
        public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
            final var defaultAnn = AnnotationUtils.synthesizeAnnotation(UpstreamLogging.class);
            final var interfaceAnn = Optional.ofNullable(k.getAnnotation(UpstreamLogging.class));

            for (Method m : k.getDeclaredMethods()) {
                final var ann = Optional.ofNullable(m.getAnnotation(UpstreamLogging.class))
                        .or(() -> interfaceAnn)
                        .orElse(defaultAnn);
                confs.put(m, ann);
            }

        }

        @Override
        public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            final var principal = ctx.principal() == null || ctx.principal().toString().isBlank() ? "" : String.format("[user:%s]", ctx.principal());
            final var conf = confs.get(ctx.method());

            final var prefix = "[boot:%s][upstream:%s][ep:%s][req:%s]%s".formatted(ctx.boot(), ctx.upstream(), ctx.endpoint(), ctx.request(), principal);

            if (conf.headers()) {
                logger.info("{}[t:oh] headers: {}", prefix, request.getHeaders());
            }
            logger.info("{}[t:ob] method: {} url: {} body: {}", prefix, request.getMethod(), request.getURI(), BodyRendering.render(conf.value(), request.getHeaders().getContentType(), body, conf.infix(), conf.maxSize()));
            try {
                final ClientHttpResponse response = execution.execute(request, body);
                final var elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
                if (conf.headers()) {
                    logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, response.getHeaders());
                }
                logger.info("{}[t:ib][ms:{}] status: {} type: {} body: {}", prefix, elapsed, response.getStatusCode(), response.getHeaders().getContentType(), BodyRendering.render(conf.value(), response.getHeaders().getContentType(), response.getBody(), conf.infix(), conf.maxSize()));
                return response;
            } catch (Exception ex) {
                final var elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
                logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
                throw ex;
            }
        }
    }

}
