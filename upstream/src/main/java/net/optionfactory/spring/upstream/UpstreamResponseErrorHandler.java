package net.optionfactory.spring.upstream;

import java.io.IOException;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public interface UpstreamResponseErrorHandler {

    default void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
    }

    boolean hasError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

    void handleError(InvocationContext invocation, RequestContext request, ResponseContext response) throws IOException;

}
