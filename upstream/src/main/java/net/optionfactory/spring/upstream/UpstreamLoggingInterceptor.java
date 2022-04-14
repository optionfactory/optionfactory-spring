package net.optionfactory.spring.upstream;

import java.time.Duration;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import net.optionfactory.spring.upstream.UpstreamPort.UpstreamBodyToString;
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
    public void before(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request) {
        final String ctxLogPrefix = contextLogEncoder.toLogPrefix(prepare.ctx);
        if (logHeaders) {
            logger.info("[upstream:{}][op:pre]{}[req:{}][ep:{}] headers={}", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId, request.headers);
        }
        final String logPrefix = String.format("[upstream:%s][op:req]%s[req:%s][ep:%s]", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId);
        
        final UpstreamBodyToString<CTX> requestToString = hints.requestToString != null ? hints.requestToString : UpstreamOps::defaultRequestToString;
        final String requestBodyAsString = requestToString.apply(prepare, request, null);
        logger.info("{} url: {} body: {}", logPrefix, prepare.entity.getUrl(), requestBodyAsString);
    }

    @Override
    public void remotingSuccess(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        final String ctxLogPrefix = contextLogEncoder.toLogPrefix(prepare.ctx);
        final long elapsedMillis = Duration.between(request.at, response.at).toMillis();
        final String logPrefix = String.format("[upstream:%s][op:res]%s[req:%s][ep:%s][ms:%s]", prepare.upstreamId, ctxLogPrefix, prepare.requestId, prepare.endpointId, elapsedMillis);
        
        final MediaType contentType = response.headers.getContentType();
        final UpstreamBodyToString<CTX> responseToString = hints.responseToString != null ? hints.responseToString : UpstreamOps::defaultResponseToString;
        final String responseBodyAsText = responseToString.apply(prepare, request, response);

        logger.info("{} status: {} type: {} body: {}", logPrefix, response.status, contentType, responseBodyAsText);
    }

    @Override
    public void remotingError(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
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
