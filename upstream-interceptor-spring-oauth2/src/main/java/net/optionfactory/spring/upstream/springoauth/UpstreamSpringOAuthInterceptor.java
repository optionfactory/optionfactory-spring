package net.optionfactory.spring.upstream.springoauth;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/// Authenticates upstream requests with an OAuth2 access token obtained by spring security's
/// `OAuth2AuthorizedClientManager`, set as `Authorization: Bearer <token>`, replacing any
/// `Authorization` header already on the request.
///
/// The manager is asked for the token on every request, always with the same authorize request:
/// obtaining, caching and refreshing the token is entirely the manager's business, and whatever
/// token it returns is used as is. For calls made outside of an http request (scheduled jobs,
/// message listeners) use an `AuthorizedClientServiceOAuth2AuthorizedClientManager`: spring's
/// `DefaultOAuth2AuthorizedClientManager` needs the current servlet request.
///
/// ```java
/// UpstreamBuilder.create(MyClient.class)
///     .initializer(new UpstreamSpringOAuthInterceptor(
///         authorizedClientManager,
///         OAuth2AuthorizeRequest.withClientRegistrationId("my-registration-id").principal("my-service").build()))
///     .baseUri("https://api.example.com")
///     .build();
/// ```
public class UpstreamSpringOAuthInterceptor implements UpstreamHttpRequestInitializer {

    private final OAuth2AuthorizedClientManager oauth;
    private final OAuth2AuthorizeRequest oauthReq;

    /// @param oauth obtains, caches and refreshes the authorized client
    /// @param oauthAuthRequest the authorization asked for on every request
    public UpstreamSpringOAuthInterceptor(OAuth2AuthorizedClientManager oauth, OAuth2AuthorizeRequest oauthAuthRequest) {
        this.oauth = oauth;
        this.oauthReq = oauthAuthRequest;
    }

    /// Sets the bearer token of the authorized client on the request.
    ///
    /// @param ctx the upstream invocation, unused
    /// @param request the request to authenticate
    /// @throws NullPointerException when the manager returns no authorized client, e.g. for a
    /// registration that cannot be authorized without a user
    /// @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException when the
    /// manager fails to obtain a token
    @Override
    public void initialize(InvocationContext ctx, ClientHttpRequest request) {
        request.getHeaders().set("Authorization", String.format("Bearer %s", oauth.authorize(oauthReq).getAccessToken().getTokenValue()));
    }

}
