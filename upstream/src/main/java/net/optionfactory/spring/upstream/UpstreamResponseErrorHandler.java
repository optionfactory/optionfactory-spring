package net.optionfactory.spring.upstream;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

public interface UpstreamResponseErrorHandler {

    default void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
    }

    boolean hasError(InvocationContext ctx, ClientHttpResponse response) throws IOException ;

    void handleError(InvocationContext ctx, ClientHttpResponse response) throws IOException;

}
