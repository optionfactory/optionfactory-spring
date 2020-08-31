package net.optionfactory.spring.upstream;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

public class UpstreamLoggingInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final ContextLogEncoder<CTX> contextLogEncoder;
    private final boolean logHeaders;
    private final boolean logMultipart;

    public UpstreamLoggingInterceptor(ContextLogEncoder<CTX> contextLogEncoder, boolean logHeaders, boolean logMultipart) {
        this.contextLogEncoder = contextLogEncoder;
        this.logHeaders = logHeaders;
        this.logMultipart = logMultipart;
    }

    @Override
    public void before(PrepareContext<CTX> prepare, RequestContext request) {
        final String ctxLogPrefix = contextLogEncoder.toLogPrefix(prepare.ctx);
        if (logHeaders) {
            logger.info("[upstream:{}][op:pre]{}[req:{}][ep:{}] headers=%s", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId, request.headers);
        }
        final String logPrefix = String.format("[upstream:%s][op:req]%s[req:%s][ep:%s]", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId);
        logger.info("{} url: {} body: {}", logPrefix, prepare.entity.getUrl(), UpstreamOps.bodyAsString(request.headers.getContentType(), logMultipart, request.body));
    }

    @Override
    public void remotingSuccess(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        final String ctxLogPrefix = contextLogEncoder.toLogPrefix(prepare.ctx);
        final long elapsedMillis = Duration.between(request.at, response.at).toMillis();
        final String logPrefix = String.format("[upstream:%s][op:res]%s[req:%s][ep:%s][ms:%s]", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId, elapsedMillis);

        final MediaType contentType = response.headers.getContentType();
        final String responseBodyAsText = UpstreamOps.bodyAsString(contentType, true, response.body);
        logger.info("%s status: {} type: {} body: {}", logPrefix, response.status, contentType, responseBodyAsText);
    }

    @Override
    public void remotingError(PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
        final String ctxLogPrefix = contextLogEncoder.toLogPrefix(prepare.ctx);
        final long elapsedMillis = Duration.between(request.at, error.at).toMillis();

        final String logPrefix = String.format("[upstream:%s][op:err]%s[req:%s][ep:%s][ms:%s]", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId, elapsedMillis);
        logger.info("{} error: {}", logPrefix, error.ex);
    }

    public interface ContextLogEncoder<CTX> {

        String toLogPrefix(CTX ctx);

        public static class Null<T> implements ContextLogEncoder<T> {

            @Override
            public String toLogPrefix(T ctx) {
                return "";
            }

        }
    }

}
