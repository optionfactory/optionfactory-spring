package net.optionfactory.spring.upstream;

import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

public interface UpstreamHttpRequestInitializer {

    default void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
    }

    void initialize(InvocationContext ctx, ClientHttpRequest request);

}
