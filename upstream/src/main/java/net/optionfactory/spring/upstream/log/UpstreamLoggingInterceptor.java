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
import net.optionfactory.spring.upstream.annotations.Annotations;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstreamLoggingInterceptor implements UpstreamHttpInterceptor {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final Map<Method, Upstream.Logging.Conf> overrides;
    private final Map<Method, Upstream.Logging.Conf> confs = new ConcurrentHashMap<>();

    public UpstreamLoggingInterceptor(Map<Method, Upstream.Logging.Conf> overrides) {
        this.overrides = overrides;
    }

    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            Annotations.closest(endpoint.method(), Upstream.Logging.class)
                    .map(a -> new Upstream.Logging.Conf(a.request(), a.requestMaxSize(), a.requestHeaders(), a.response(), a.responseMaxSize(), a.responseHeaders(), a.infix()))
                    .ifPresent(ann -> confs.put(endpoint.method(), ann));
        }
    }

    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final Method m = invocation.endpoint().method();
        final var conf = Optional.ofNullable(overrides.get(m)).orElse(confs.get(m));
        if (conf == null) {
            return execution.execute(invocation, request);
        }
        final var principal = invocation.principal() == null || invocation.principal().toString().isBlank() ? "" : String.format("[user:%s]", invocation.principal());
        final var prefix = "[boot:%s][upstream:%s][ep:%s][req:%s]%s".formatted(invocation.boot(), invocation.endpoint().upstream(), invocation.endpoint().name(), request.id(), principal);
        if (conf.requestHeaders() != BodyRendering.HeadersStrategy.SKIP) {
            logger.info("{}[t:oh] headers: {}", prefix, request.headers());
        }
        if (conf.request() != BodyRendering.Strategy.SKIP) {
            final var requestBody = request.body() == null || request.body().length == 0 ? "" : String.format(" body: %s", BodyRendering.render(conf.request(), request.headers().getContentLength(), request.headers().getContentType(), BodySource.of(request.body()), conf.infix(), conf.requestMaxSize()));
            logger.info("{}[t:ob] method: {} url: {}{}", prefix, request.method(), request.uri(), requestBody);
        }
        try {
            final ResponseContext response = execution.execute(invocation, request);
            final long elapsed = Duration.between(request.at(), response.at()).toMillis();
            if (conf.responseHeaders() != BodyRendering.HeadersStrategy.SKIP) {
                logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, response.headers());
            }
            if (conf.response() != BodyRendering.Strategy.SKIP) {
                final var responseBody = response.headers().getContentLength() == 0 ? "" : String.format(" type:%s body: %s", response.headers().getContentType(), BodyRendering.render(conf.response(), response.headers().getContentLength(), response.headers().getContentType(), response.body(), conf.infix(), conf.responseMaxSize()));
                logger.info("{}[t:ib][ms:{}] status: {}{}", prefix, elapsed, response.status(), responseBody);
            }
            return response;
        } catch (Exception ex) {
            final long elapsed = Duration.between(request.at(), Instant.now()).toMillis();
            logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
            throw ex;
        }
    }

}
