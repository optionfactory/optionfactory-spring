package net.optionfactory.spring.upstream.springoauth;

import net.optionfactory.spring.upstream.UpstreamInterceptor;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class UpstreamOAuthInterceptor<T> implements UpstreamInterceptor<T> {

    private final OAuth2AuthorizedClientManager oauth;
    private final OAuth2AuthorizeRequest oauthReq;

    public UpstreamOAuthInterceptor(OAuth2AuthorizedClientManager oauth, OAuth2AuthorizeRequest oauthAuthRequest) {
        this.oauth = oauth;
        this.oauthReq = oauthAuthRequest;
    }

    @Override
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("Bearer %s", oauth.authorize(oauthReq).getAccessToken().getTokenValue()));
        return headers;
    }

}
