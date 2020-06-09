package net.optionfactory.spring.upstream;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamFaultsSpooler.UpstreamFault;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class UpstreamFaultsInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final UpstreamFaultsSpooler<CTX> faults;
    private final UpstreamTracingInterceptor<CTX> tracing;

    public UpstreamFaultsInterceptor(UpstreamTracingInterceptor<CTX> tracing, UpstreamFaultsSpooler<CTX> faults) {
        this.tracing = tracing;
        this.faults = faults;
    }

    @Override
    public void after(String upstreamId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody, HttpStatus responseStatus, HttpHeaders responseHeaders, Resource responseBody) {
        if (responseStatus == null) {
            return;
        }
        if (!responseStatus.is4xxClientError() && !responseStatus.is5xxServerError()) {
            return;
        }
        final MediaType contentType = responseHeaders.getContentType();
        final String responseBodyAsText = UpstreamOps.bodyAsString(contentType, true, responseBody);
        final CTX ctx = tracing.context(requestHeaders);

        final String requestId = tracing.requestId(requestHeaders);
        final Instant requestInstant = tracing.timestamp(requestHeaders);
        final String requestBodyAsString = UpstreamOps.bodyAsString(MediaType.TEXT_XML /*fixme*/, true, requestBody);
        final Instant responseInstant = Instant.now();
        
        faults.add(UpstreamFault.of(ctx, requestId, requestUri, responseStatus, contentType, requestInstant, requestBodyAsString, responseInstant, responseBodyAsText, null));
    }

    @Override
    public void error(String upstreamId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody, Exception ex) {
        final CTX ctx = tracing.context(requestHeaders);

        final String requestId = tracing.requestId(requestHeaders);
        final Instant requestInstant = tracing.timestamp(requestHeaders);
        final String requestBodyAsString = UpstreamOps.bodyAsString(MediaType.TEXT_XML /*fixme*/, true, requestBody);
        final Instant responseInstant = Instant.now();

        faults.add(UpstreamFault.of(ctx, requestId, requestUri, null, null, requestInstant, requestBodyAsString, responseInstant, null, ex));
    }

}
