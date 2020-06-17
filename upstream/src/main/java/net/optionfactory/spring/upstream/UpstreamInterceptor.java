package net.optionfactory.spring.upstream;

import java.net.URI;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

public interface UpstreamInterceptor<PREPARE_CONTEXT> {

    default HttpHeaders prepare(String upstreamId, String endpointId, PREPARE_CONTEXT ctx, RequestEntity<?> entity) {
        return null;
    }

    default void before(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody) {
    }

    default void after(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody, HttpStatus responseStatus, HttpHeaders responseHeaders, Resource responseBody) {
    }

    default void error(String upstreamId, String endpointId, HttpHeaders requestHeaders, URI requestUri, Resource requestBody, Exception ex) {
    }
}
