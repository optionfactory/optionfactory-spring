package net.optionfactory.spring.upstream.auth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

public class StaticTokenAuthenticator implements UpstreamHttpRequestInitializer {

    private final String tokenType;
    private final String token;

    public StaticTokenAuthenticator(String tokenType, String token) {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        request.getHeaders().add("Authorization", String.format("%s %s", tokenType, token));
    }

}
