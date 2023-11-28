package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;

public class UpstreamLoggingInterceptor implements UpstreamHttpInterceptor {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final Map<Method, Upstream.Logging> confs = new ConcurrentHashMap<>();

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var interfaceAnn = Optional.ofNullable(k.getAnnotation(Upstream.Logging.class));
        for (Method m : k.getDeclaredMethods()) {
            Optional.ofNullable(m.getAnnotation(Upstream.Logging.class))
                    .or(() -> interfaceAnn)
                    .ifPresent(ann -> confs.put(m, ann));
        }
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final var principal = invocation.principal() == null || invocation.principal().toString().isBlank() ? "" : String.format("[user:%s]", invocation.principal());
        final var conf = confs.get(invocation.method());
        if (conf == null) {
            return execution.execute(invocation, request);
        }
        final var prefix = "[boot:%s][upstream:%s][ep:%s][req:%s]%s".formatted(invocation.boot(), invocation.upstream(), invocation.endpoint(), request.id(), principal);
        if (conf.headers()) {
            logger.info("{}[t:oh] headers: {}", prefix, request.headers());
        }
        logger.info("{}[t:ob] method: {} url: {} body: {}", prefix, request.method(), request.uri(), BodyRendering.render(conf.value(), request.headers().getContentType(), request.body(), conf.infix(), conf.maxSize()));
        try {
            final ResponseContext response = execution.execute(invocation, request);
            final long elapsed = Duration.between(request.at(), response.at()).toMillis();
            if (conf.headers()) {
                logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, response.headers());
            }
            logger.info("{}[t:ib][ms:{}] status: {} type: {} body: {}", prefix, elapsed, response.status(), response.headers().getContentType(), BodyRendering.render(conf.value(), response.headers().getContentType(), response.body(), conf.infix(), conf.maxSize()));
            return response;
        } catch (Exception ex) {
            final long elapsed = Duration.between(request.at(), Instant.now()).toMillis();
            logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
            throw ex;
        }
    }

}
