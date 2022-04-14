package net.optionfactory.spring.upstream;

import java.time.Instant;
import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

public interface UpstreamInterceptor<CTX> {

    public static class PrepareContext<CTX> {

        public String upstreamId;
        public String endpointId;
        public String requestId;
        public CTX ctx;
        public RequestEntity<?> entity;
    }

    public static class RequestContext {

        public Instant at;
        public HttpHeaders headers;
        public Resource body;
    }

    public static class ResponseContext {

        public Instant at;
        public HttpStatus status;
        public HttpHeaders headers;
        public Resource body;
    }

    public static class ErrorContext {

        public Instant at;
        public Exception ex;
    }

    public static class ExchangeContext<CTX> {
        public Hints<CTX> hints;
        public PrepareContext<CTX> prepare;
        public RequestContext request;
        public ResponseContext response;
        public ErrorContext error;
    }

    default HttpHeaders prepare(Hints<CTX> hints, PrepareContext<CTX> prepare) {
        return null;
    }

    default void before(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request) {
    }

    default void remotingSuccess(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
    }

    default Optional<UpstreamResult> errorStrategy(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        return Optional.empty();
    }

    default void remotingError(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
    }

    default void mappingSuccess(Hints<CTX> hints, PrepareContext<CTX> prepare, RequestContext request, ResponseContext response, ResponseEntity<?> mapped) {

    }

    public static class UpstreamResult {

        public boolean success;
        public String failureReason;
        public String failureDetails;
    }

}
