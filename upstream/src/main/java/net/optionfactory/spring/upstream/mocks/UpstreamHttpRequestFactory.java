package net.optionfactory.spring.upstream.mocks;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

public interface UpstreamHttpRequestFactory {

    ClientHttpRequest createRequest(UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod httpMethod) throws IOException;

}
