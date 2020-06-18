package net.optionfactory.spring.upstream;

import java.time.Instant;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

public interface UpstreamInterceptor<CTX> {
    
    
    public static class PrepareContext<CTX> {
        public String upstreamId;
        public String endpointId;
        public long requestId;
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
        public PrepareContext<CTX> prepare;
        public RequestContext request;
        public ResponseContext response;
        public ErrorContext error;
    }

    default HttpHeaders prepare(PrepareContext<CTX> prepare) {
        return null;
    }

    default void before(PrepareContext<CTX> prepare, RequestContext request) {
    }

    default void success(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
    }

    default void error(PrepareContext<CTX> prepare, RequestContext request, ErrorContext error) {
    }
}
