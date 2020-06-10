package net.optionfactory.spring.upstream;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

public interface UpstreamPort<CTX> {

    <T> ResponseEntity<T> exchange(CTX context, RequestEntity<?> requestEntity, Class<T> responseType);

    <T> ResponseEntity<T> exchange(CTX context, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType);

}
