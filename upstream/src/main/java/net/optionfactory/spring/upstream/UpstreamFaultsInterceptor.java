package net.optionfactory.spring.upstream;

import java.net.URI;
import java.time.Instant;
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
    public void success(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        if (response.status == null) {
            return;
        }
        if (!response.status.is4xxClientError() && !response.status.is5xxServerError()) {
            return;
        }
        final MediaType contentType = response.headers.getContentType();
        final String responseBodyAsText = UpstreamOps.bodyAsString(contentType, true, response.body);

        final String requestBodyAsString = UpstreamOps.bodyAsString(MediaType.TEXT_XML /*fixme*/, true, request.body);
        
        faults.add(UpstreamFault.of(
                prepare.ctx, 
                prepare.requestId, 
                prepare.entity.getUrl(), 
                response.status,
                contentType, 
                request.at, 
                requestBodyAsString, 
                response.at, 
                responseBodyAsText, 
                null
        ));
    }

    @Override
    public void error(PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
        final String requestBodyAsString = UpstreamOps.bodyAsString(MediaType.TEXT_XML /*fixme*/, true, request.body);

        faults.add(UpstreamFault.of(
                prepare.ctx, 
                prepare.requestId, 
                prepare.entity.getUrl(), 
                null, 
                null, 
                request.at, 
                requestBodyAsString, 
                error.at, 
                null, 
                error.ex));
    }

}
