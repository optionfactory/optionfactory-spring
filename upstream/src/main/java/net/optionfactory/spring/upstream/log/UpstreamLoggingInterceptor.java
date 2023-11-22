package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

public class UpstreamLoggingInterceptor implements UpstreamHttpInterceptor {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final Map<Method, Upstream.Logging> confs = new ConcurrentHashMap<>();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var defaultAnn = AnnotationUtils.synthesizeAnnotation(Upstream.Logging.class);
        final var interfaceAnn = Optional.ofNullable(k.getAnnotation(Upstream.Logging.class));
        for (Method m : k.getDeclaredMethods()) {
            final var ann = Optional.ofNullable(m.getAnnotation(Upstream.Logging.class)).or(() -> interfaceAnn).orElse(defaultAnn);
            confs.put(m, ann);
        }
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        final var principal = ctx.principal() == null || ctx.principal().toString().isBlank() ? "" : String.format("[user:%s]", ctx.principal());
        final var conf = confs.get(ctx.method());
        final java.lang.String prefix = "[boot:%s][upstream:%s][ep:%s][req:%s]%s".formatted(ctx.boot(), ctx.upstream(), ctx.endpoint(), ctx.request(), principal);
        if (conf.headers()) {
            logger.info("{}[t:oh] headers: {}", prefix, request.getHeaders());
        }
        logger.info("{}[t:ob] method: {} url: {} body: {}", prefix, request.getMethod(), request.getURI(), BodyRendering.render(conf.value(), request.getHeaders().getContentType(), body, conf.infix(), conf.maxSize()));
        try {
            final ClientHttpResponse response = execution.execute(request, body);
            final long elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
            if (conf.headers()) {
                logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, response.getHeaders());
            }
            logger.info("{}[t:ib][ms:{}] status: {} type: {} body: {}", prefix, elapsed, response.getStatusCode(), response.getHeaders().getContentType(), BodyRendering.render(conf.value(), response.getHeaders().getContentType(), response.getBody(), conf.infix(), conf.maxSize()));
            return response;
        } catch (Exception ex) {
            final long elapsed = Duration.between(ctx.requestedAt(), ctx.clock().instant()).toMillis();
            logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
            throw ex;
        }
    }

}
