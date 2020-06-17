package net.optionfactory.spring.upstream;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;

public class UpstreamLoggingInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLoggingInterceptor.class);
    private final UpstreamTracingInterceptor<CTX> tracing;
    private final boolean logHeaders;
    private final boolean logMultipart;

    public UpstreamLoggingInterceptor(UpstreamTracingInterceptor<CTX> tracing, boolean logHeaders, boolean logMultipart) {
        this.tracing = tracing;
        this.logHeaders = logHeaders;
        this.logMultipart = logMultipart;
    }

    @Override
    public HttpHeaders prepare(String upstreamId, String endpointId, CTX userId, RequestEntity<?> entity) {
        return null;
    }

    @Override
    public void before(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody) {
        final String requestId = tracing.requestId(requestHeaders);
        final String ctxLogPrefix = tracing.logPrefix(requestHeaders);
        if (logHeaders) {
            logger.info(String.format("[upstream:%s][op:pre]%s[req:%s][ep:%s] headers=%s", upstreamId, ctxLogPrefix, requestId, endpointId, requestHeaders));
        }
        final String logPrefix = String.format("[upstream:%s][op:req]%s[req:%s][ep:%s]", upstreamId, ctxLogPrefix, requestId, endpointId);
        logger.info(String.format("%s url: %s body: %s", logPrefix, requestUri, UpstreamOps.bodyAsString(requestHeaders.getContentType(), logMultipart, requestBody)));
    }


    @Override
    public void after(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri,  Resource requestBody, HttpStatus responseStatus, HttpHeaders responseHeaders, Resource responseBody) {

        final String ctxLogPrefix = tracing.logPrefix(requestHeaders);

        final String requestId = tracing.requestId(requestHeaders);
        final Instant requestInstant = tracing.timestamp(requestHeaders);
        final Instant responseInstant = Instant.now();
        final long elapsedMillis = Duration.between(requestInstant, responseInstant).toMillis();
        final String logPrefix = String.format("[upstream:%s][op:res]%s[req:%s][ep:%s][ms:%s]", upstreamId, ctxLogPrefix, requestId, endpointId, elapsedMillis);

        final MediaType contentType = responseHeaders.getContentType();
        final String responseBodyAsText = UpstreamOps.bodyAsString(contentType, true, responseBody);
        logger.info(String.format("%s status: %s type: %s body: %s", logPrefix, responseStatus, contentType, responseBodyAsText));
    }

    @Override
    public void error(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri,  Resource requestBody, Exception ex) {

        final String ctxLogPrefix = tracing.logPrefix(requestHeaders);

        final String requestId = tracing.requestId(requestHeaders);
        final Instant requestInstant = tracing.timestamp(requestHeaders);
        final Instant responseInstant = Instant.now();
        final long elapsedMillis = Duration.between(requestInstant, responseInstant).toMillis();

        final String logPrefix = String.format("[upstream:%s][op:err]%s[req:%s][ep:%s][ms:%s]", upstreamId, ctxLogPrefix, requestId, endpointId, elapsedMillis);
        logger.info(String.format("%s error: %s", logPrefix, ex));
    }

}
