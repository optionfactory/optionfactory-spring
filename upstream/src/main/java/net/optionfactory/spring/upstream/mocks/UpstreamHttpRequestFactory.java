package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

public interface UpstreamHttpRequestFactory {

    ClientHttpRequest createRequest(InvocationContext invocation, URI uri, HttpMethod httpMethod) throws IOException;

}
