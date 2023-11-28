package net.optionfactory.spring.upstream;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.http.client.ClientHttpRequestFactory;

public interface UpstreamAfterMappingHandler {

    void preprocess(Class<?> k, ClientHttpRequestFactory rf);

    void handle(InvocationContext invocation, RequestContext request, ResponseContext response, Object result);
}
