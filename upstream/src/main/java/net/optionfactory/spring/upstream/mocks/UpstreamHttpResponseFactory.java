package net.optionfactory.spring.upstream.mocks;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;

public interface UpstreamHttpResponseFactory {

    default void preprocess(Class<?> k, Expressions expressions, Map<Method, EndpointDescriptor> endpoints) {
    }


    ClientHttpResponse create(InvocationContext invocation, URI uri, HttpMethod method, HttpHeaders headers);

}
