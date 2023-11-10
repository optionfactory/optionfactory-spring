package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.BodyRendering;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface UpstreamLogging {

    public enum Strategy {
        SIZE, ABBREVIATED, ABBREVIATED_ONELINE;
    }

    Strategy value() default Strategy.ABBREVIATED_ONELINE;

    boolean headers() default true;

    public static class Interceptor implements UpstreamHttpInterceptor {

        private final Logger logger = LoggerFactory.getLogger(Interceptor.class);
        private final Map<Method, UpstreamLogging> confs = new ConcurrentHashMap<>();

        @Override
        public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
            final var defaultValues = AnnotationUtils.synthesizeAnnotation(UpstreamLogging.class);
            for (Method m : k.getDeclaredMethods()) {
                UpstreamLogging ann = AnnotationUtils.findAnnotation(m, UpstreamLogging.class);
                confs.put(m, ann != null ? ann : defaultValues);
            }

        }

        @Override
        public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            final var principal = ctx.principal() == null || ctx.principal().toString().isBlank() ? "" : String.format("[user:%s]", ctx.principal());
            final var conf = confs.get(ctx.method());
            if (conf.headers()) {
                logger.info("[upstream:{}][op:req]{}[req:{}.{}][ep:{}] headers: {}", ctx.upstreamId(), principal, ctx.bootId(), ctx.requestId(), ctx.method().getName(), request.getHeaders());
            }
            logger.info("[upstream:{}][op:req]{}[req:{}.{}][ep:{}] url: {} body: {}", ctx.upstreamId(), principal, ctx.bootId(), ctx.requestId(), ctx.method().getName(), request.getURI(), bodyAsText(body, request.getHeaders().getContentType(), conf.value()));
            try {
                final ClientHttpResponse response = execution.execute(request, body);
                final var elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
                if (conf.headers()) {
                    logger.info("[upstream:{}][op:res]{}[req:{}.{}][ep:{}][ms:{}] headers: {}", ctx.upstreamId(), principal, ctx.bootId(), ctx.requestId(), ctx.method().getName(), elapsed, response.getHeaders());
                }
                logger.info("[upstream:{}][op:res]{}[req:{}.{}][ep:{}][ms:{}] status: {} type: {} body: {}", ctx.upstreamId(), principal, ctx.bootId(), ctx.requestId(), ctx.method().getName(), elapsed, response.getStatusCode(), response.getHeaders().getContentType(), bodyAsText(response.getBody(), response.getHeaders().getContentType(), conf.value()));
                return response;
            } catch (Exception ex) {
                final var elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
                logger.info("[upstream:{}][op:res]{}[req:{}.{}][ep:{}][ms:{}] error: {}", ctx.upstreamId(), principal, ctx.bootId(), ctx.requestId(), ctx.method().getName(), elapsed, ex);
                throw ex;
            }
        }

        private static String bodyAsText(InputStream is, MediaType mediaType, Strategy strategy) {
            try (is) {
                return bodyAsText(StreamUtils.copyToByteArray(is), mediaType, strategy);
            } catch (IOException ex) {
                return "(unreadable)";
            }
        }

        private static String bodyAsText(byte[] body, MediaType mediaType, Strategy strategy) {
            return switch (strategy) {
                case SIZE ->
                    String.format("%sbytes", body.length);
                case ABBREVIATED ->
                    BodyRendering.abbreviated(body, "✂️", 8 * 1024);
                case ABBREVIATED_ONELINE ->
                    BodyRendering.abbreviated(body, "✂️", 8 * 1024).replaceAll("[\r\n]+", "");
            };
        }
    }

}
