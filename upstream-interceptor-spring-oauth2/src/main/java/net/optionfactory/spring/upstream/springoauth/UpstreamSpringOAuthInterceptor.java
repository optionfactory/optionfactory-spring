package net.optionfactory.spring.upstream.springoauth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class UpstreamSpringOAuthInterceptor implements UpstreamHttpRequestInitializer {

    private final OAuth2AuthorizedClientManager oauth;
    private final OAuth2AuthorizeRequest oauthReq;

    public UpstreamSpringOAuthInterceptor(OAuth2AuthorizedClientManager oauth, OAuth2AuthorizeRequest oauthAuthRequest) {
        this.oauth = oauth;
        this.oauthReq = oauthAuthRequest;
    }

    @Override
    public void initialize(InvocationContext ctx, ClientHttpRequest request) {
        request.getHeaders().set("Authorization", String.format("Bearer %s", oauth.authorize(oauthReq).getAccessToken().getTokenValue()));
    }

}
