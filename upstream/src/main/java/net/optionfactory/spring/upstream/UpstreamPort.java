package net.optionfactory.spring.upstream;

import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamInterceptor.PrepareContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ResponseContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.UpstreamResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

public interface UpstreamPort<CTX> {

    <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, Class<T> responseType, Hints<CTX> hints);

    <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, Hints<CTX> hints);

    public static class Hints<CTX> {

        public UpstreamErrorHandler<CTX> errorHandler;
        public UpstreamFaultPredicate<CTX> isFault;
        public UpstreamBodyToString<CTX> requestToString;
        public UpstreamBodyToString<CTX> responseToString;
        
        
        public static <CTX> Hints<CTX> empty(Class<CTX> cls) {
            return new Hints<>();
        }

        public Hints<CTX> with(UpstreamErrorHandler<CTX> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Hints<CTX> with(UpstreamFaultPredicate<CTX> isFault) {
            this.isFault = isFault;
            return this;
        }

        public Hints<CTX> with(UpstreamBodyToString<CTX> requestToString, UpstreamBodyToString<CTX> responseToString) {
            this.requestToString = requestToString;
            this.responseToString = responseToString;
            return this;
        }

    }

    public static interface UpstreamErrorHandler<CTX> {

        Optional<UpstreamResult> apply(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response);
    }

    public static interface UpstreamFaultPredicate<CTX> {

        boolean apply(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response);
    }

    public static interface UpstreamBodyToString<CTX> {

        String apply(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response);
    }

}
