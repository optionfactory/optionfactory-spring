package net.optionfactory.spring.upstream.mocks;

import java.net.URI;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

public interface UpstreamHttpResponseFactory {

    default void prepare(Class<?> klass) {
    }

    ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers);

}
