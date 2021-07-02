package net.optionfactory.spring.upstream;

import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamInterceptor.PrepareContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ResponseContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.UpstreamResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

public interface UpstreamPort<CTX> {

    <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, Class<T> responseType, @Nullable UpstreamErrorHandler<CTX> errorStrategy);

    <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, @Nullable UpstreamErrorHandler<CTX> errorStrategy);

    default <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, Class<T> responseType) {
        return exchange(context, endpoint, requestEntity, responseType, null);
    }

    default <T> ResponseEntity<T> exchange(CTX context, String endpoint, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        return exchange(context, endpoint, requestEntity, responseType, null);
    }

    public static interface UpstreamErrorHandler<CTX> {

        Optional<UpstreamResult> apply(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response);
    }

}
