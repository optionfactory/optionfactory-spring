package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;

public interface UpstreamHttpInterceptor {

    default void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
    }

    default ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        return execution.execute(request, body);
    }

    public static record InvocationContext(
            String upstream,
            List<HttpMessageConverter<?>> converters,
            InstantSource clock,
            Instant requestedAt,
            String endpoint,
            Method method,
            Object[] arguments,
            String boot,
            Object principal,
            long request) {

    }
}
