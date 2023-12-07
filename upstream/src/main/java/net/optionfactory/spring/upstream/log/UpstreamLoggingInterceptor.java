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
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
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
        if (conf.requestHeaders()) {
            logger.info("{}[t:oh] headers: {}", prefix, request.headers());
        }
        final var requestBody = request.body() == null || request.body().length == 0 ? "" : String.format(" body: %s", BodyRendering.render(conf.request(), request.headers().getContentLength(), request.headers().getContentType(), BodySource.of(request.body()), conf.infix(), conf.requestMaxSize()));
        logger.info("{}[t:ob] method: {} url: {}{}", prefix, request.method(), request.uri(), requestBody);
        try {
            final ResponseContext response = execution.execute(invocation, request);
            final long elapsed = Duration.between(request.at(), response.at()).toMillis();
            if (conf.responseHeaders()) {
                logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, response.headers());
            }
            final var responseBody = response.headers().getContentLength() == 0 ? "" : String.format(" type:%s body: %s", response.headers().getContentType(), BodyRendering.render(conf.response(), response.headers().getContentLength(), response.headers().getContentType(), response.body(), conf.infix(), conf.responseMaxSize()));
            logger.info("{}[t:ib][ms:{}] status: {}{}", prefix, elapsed, response.status(), responseBody);
            return response;
        } catch (Exception ex) {
            final long elapsed = Duration.between(request.at(), Instant.now()).toMillis();
            logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
            throw ex;
        }
    }

}
