package net.optionfactory.spring.upstream;

import net.optionfactory.spring.upstream.UpstreamFaultsSpooler.UpstreamFault;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import net.optionfactory.spring.upstream.UpstreamPort.UpstreamBodyToString;
import net.optionfactory.spring.upstream.UpstreamPort.UpstreamFaultPredicate;
import org.springframework.http.MediaType;

public class UpstreamFaultsInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final UpstreamFaultsSpooler<CTX> faults;

    public UpstreamFaultsInterceptor(UpstreamFaultsSpooler<CTX> faults) {
        this.faults = faults;
    }

    @Override
    public void remotingSuccess(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        final UpstreamFaultPredicate<CTX> isFault = hints.isFault != null ? hints.isFault : UpstreamOps::defaultFaultStrategy;
        if (!isFault.apply(prepare, request, response)){
            return;
        }
        final UpstreamBodyToString<CTX> requestToString = hints.requestToString != null ? hints.requestToString : UpstreamOps::defaultRequestToString;
        final String requestBodyAsString = requestToString.apply(prepare, request, response);

        final UpstreamBodyToString<CTX> responseToString = hints.responseToString != null ? hints.responseToString : UpstreamOps::defaultResponseToString;
        final String responseBodyAsText = responseToString.apply(prepare, request, response);

        faults.add(UpstreamFault.of(
                prepare.ctx,
                prepare.requestId,
                prepare.entity.getUrl(),
                response.status,
                response.headers.getContentType(),
                request.at,
                requestBodyAsString,
                response.at,
                responseBodyAsText,
                null
        ));
    }

    @Override
    public void remotingError(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
        final UpstreamBodyToString<CTX> requestToString = hints.requestToString != null ? hints.requestToString : UpstreamOps::defaultRequestToString;
        final String requestBodyAsString = requestToString.apply(prepare, request, null);

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
