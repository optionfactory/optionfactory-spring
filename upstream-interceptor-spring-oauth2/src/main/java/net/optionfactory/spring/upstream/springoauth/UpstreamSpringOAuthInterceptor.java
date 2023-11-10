package net.optionfactory.spring.upstream.springoauth;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class UpstreamSpringOAuthInterceptor implements UpstreamHttpInterceptor {

    private final OAuth2AuthorizedClientManager oauth;
    private final OAuth2AuthorizeRequest oauthReq;

    public UpstreamSpringOAuthInterceptor(OAuth2AuthorizedClientManager oauth, OAuth2AuthorizeRequest oauthAuthRequest) {
        this.oauth = oauth;
        this.oauthReq = oauthAuthRequest;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set("Authorization", String.format("Bearer %s", oauth.authorize(oauthReq).getAccessToken().getTokenValue()));
        return execution.execute(request, body);
    }

}
