package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequest;

public class UpstreamStaticTokenAuthenticator implements UpstreamHttpRequestInitializer {

    private final String tokenType;
    private final String token;

    public UpstreamStaticTokenAuthenticator(String tokenType, String token) {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public void initialize(UpstreamHttpInterceptor.InvocationContext ctx, ClientHttpRequest request) {
        request.getHeaders().add("Authorization", String.format("%s %s", tokenType, token));
    }

}
