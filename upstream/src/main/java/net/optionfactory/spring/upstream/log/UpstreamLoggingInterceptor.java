package net.optionfactory.spring.upstream.log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.InstantSource;
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
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/// Logs the exchanges of the endpoints configured by `@Upstream.Logging`, or by the configurations
/// given to [net.optionfactory.spring.upstream.UpstreamBuilder#logging(Upstream.Logging.Conf)], at
/// `INFO` on the `net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor` logger.
///
/// The configuration of an endpoint is, in order of precedence: the one given for its method, the one
/// given for every method, the closest `@Upstream.Logging` annotation (see
/// [net.optionfactory.spring.upstream.annotations.Annotations]). An endpoint with none is not
/// logged at all.
///
/// Each line starts with `[boot:...][upstream:...][ep:...][req:...]`, followed by `[user:...]` when
/// the invocation has a principal whose string form is not blank, and by a tag: `[t:oh]` and
/// `[t:ob]` for the outgoing headers and body, `[t:ih]` and `[t:ib]` for the incoming ones, `[t:ie]`
/// for a failed exchange; the incoming and failure lines also carry the elapsed milliseconds
/// (`[ms:...]`). Payloads are rendered, and redacted, by the
/// [net.optionfactory.spring.upstream.rendering.PayloadsRendering] of the invocation. The request
/// is logged before it is sent.
///
/// Always registered by [net.optionfactory.spring.upstream.UpstreamBuilder].
public class UpstreamLoggingInterceptor implements UpstreamHttpInterceptor {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final Optional<Upstream.Logging.Conf> override;
    private final Map<Method, Upstream.Logging.Conf> overrides;
    private final Map<Method, Upstream.Logging.Conf> confs = new ConcurrentHashMap<>();
    private final InstantSource clock;

    /// @param override the configuration of every endpoint, winning over the annotations
    /// @param overrides the configurations of single endpoints, winning over everything else
    /// @param clock the clock timestamping the requests of the client, measuring the elapsed time of a
    /// failed exchange
    public UpstreamLoggingInterceptor(Optional<Upstream.Logging.Conf> override, Map<Method, Upstream.Logging.Conf> overrides, InstantSource clock) {
        this.override = override;
        this.overrides = overrides;
        this.clock = clock;
    }

    /// Creates an interceptor measuring the elapsed time of a failed exchange with the system clock,
    /// for clients built with no [net.optionfactory.spring.upstream.UpstreamBuilder#clock].
    ///
    /// @param override the configuration of every endpoint, winning over the annotations
    /// @param overrides the configurations of single endpoints, winning over everything else
    public UpstreamLoggingInterceptor(Optional<Upstream.Logging.Conf> override, Map<Method, Upstream.Logging.Conf> overrides) {
        this(override, overrides, InstantSource.system());
    }

    /// Reads the closest `@Upstream.Logging` of every endpoint: on the method, or else on the
    /// interface hierarchy.
    ///
    /// @param k the proxied interface
    /// @param expressions the expressions of the client, unused
    /// @param endpoints the endpoints of the client, by method
    @Override
    public void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
        for (final var endpoint : endpoints.values()) {
            Annotations.closest(endpoint.method(), k, Upstream.Logging.class)
                    .map(a -> new Upstream.Logging.Conf(
                    a.requestMultipart(),
                    a.requestHeaders(),
                    a.requestBody(),
                    a.requestMaxSize(),
                    a.responseMultipart(),
                    a.responseHeaders(),
                    a.responseBody(),
                    a.responseMaxSize(),
                    a.infix()
            ))
                    .ifPresent(ann -> confs.put(endpoint.method(), ann));
        }
    }

    /// @param invocation the invocation in progress
    /// @param request the request to send
    /// @param execution the rest of the chain
    /// @return the response, untouched
    /// @throws IOException the failure of the exchange, rethrown after being logged
    @Override
    public ResponseContext intercept(InvocationContext invocation, RequestContext request, UpstreamHttpRequestExecution execution) throws IOException {
        final Method m = invocation.endpoint().method();
        final var conf = Optional.ofNullable(overrides.get(m)).or(() -> override).orElseGet(() -> confs.get(m));
        if (conf == null) {
            return execution.execute(invocation, request);
        }
        final var principal = invocation.principal() == null || invocation.principal().toString().isBlank() ? "" : String.format("[user:%s]", invocation.principal());
        final var prefix = "[boot:%s][upstream:%s][ep:%s][req:%s]%s".formatted(invocation.boot(), invocation.endpoint().upstream(), invocation.endpoint().name(), invocation.id(), principal);

        final var renderedRequest = invocation.rendering().render(request, conf.requestMultipart(), conf.requestHeaders(), conf.requestBody(), conf.infix(), conf.requestMaxSize());

        if (conf.requestHeaders() != PayloadsRendering.HeadersStrategy.SKIP) {
            logger.info("{}[t:oh] headers: {}", prefix, renderedRequest.main().headers());
        }
        if (conf.requestBody() != PayloadsRendering.BodiesStrategy.SKIP) {

            logger.info("{}[t:ob] method: {} url: {}{}", prefix, request.method(), renderedRequest.uri(), typeAndBodySuffix(renderedRequest.main().headers().getContentType(), renderedRequest.main().body()));

            final var parts = renderedRequest.parts();
            for (int i = 0; i != parts.size(); i++) {
                final var part = parts.get(i);
                logger.info("{}[t:ob][part:{}/{}]{}", prefix, i+1, parts.size(), typeAndBodySuffix(part.headers().getContentType(), part.body()));
            }
        }
        try {
            final var response = execution.execute(invocation, request);
            final long elapsed = Duration.between(request.at(), response.at()).toMillis();

            final var renderedResponse = invocation.rendering().render(response, conf.responseMultipart(), conf.responseHeaders(), conf.responseBody(), conf.infix(), conf.responseMaxSize());

            if (conf.responseHeaders() != PayloadsRendering.HeadersStrategy.SKIP) {
                logger.info("{}[t:ih][ms:{}] headers: {}", prefix, elapsed, renderedResponse.main().headers());
            }
            if (conf.responseBody() != PayloadsRendering.BodiesStrategy.SKIP) {
                logger.info("{}[t:ib][ms:{}] status: {}{}", prefix, elapsed, response.status(), typeAndBodySuffix(renderedResponse.main().headers().getContentType(), renderedResponse.main().body()));
                final var parts = renderedResponse.parts();
                for (int i = 0; i != parts.size(); i++) {
                    final var part = parts.get(i);
                    logger.info("{}[t:ib][ms:{}][part:{}/{}]{}", prefix, elapsed, i+1, parts.size(), typeAndBodySuffix(part.headers().getContentType(), part.body()));
                }
            }
            return response;
        } catch (Exception ex) {
            final long elapsed = Duration.between(request.at(), clock.instant()).toMillis();
            logger.info("{}[t:ie][ms:{}] error: {}", prefix, elapsed, ex);
            throw ex;
        }
    }

    private static String typeAndBodySuffix(MediaType type, String body) {
        final var sb = new StringBuilder();
        if (type != null) {
            sb.append(" type: ").append(type);
        }
        if (!body.isEmpty()) {
            sb.append(" body: ").append(body);
        }
        return sb.toString();
    }
}
