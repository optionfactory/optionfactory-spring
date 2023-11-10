package net.optionfactory.spring.upstream.auth;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

public class UpstreamStaticAuthorizationTokenInterceptor implements UpstreamHttpInterceptor {

    private final String tokenType;
    private final String token;

    public UpstreamStaticAuthorizationTokenInterceptor(String tokenType, String token) {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add("Authorization", String.format("%s %s", tokenType, token));
        return execution.execute(request, body);
    }

}
