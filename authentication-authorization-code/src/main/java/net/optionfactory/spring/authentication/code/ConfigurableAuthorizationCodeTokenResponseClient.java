package net.optionfactory.spring.authentication.code;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/// Exchanges the authorization code for the tokens, as spring's
/// `RestClientAuthorizationCodeTokenResponseClient` does, but through a configurable
/// `ClientHttpRequestFactory`, so that proxies, timeouts and tls settings apply to the call to the
/// token endpoint too.
///
/// An error response from the token endpoint is read as an OAuth2 error, so the failure carries the
/// identity provider's error code (e.g. `invalid_grant`). The response is parsed as json: the
/// application needs a json library spring supports (jackson, gson, json-b) on its classpath.
///
/// ```java
/// http.oauth2Login(login -> login.tokenEndpoint(t -> t.accessTokenResponseClient(new ConfigurableAuthorizationCodeTokenResponseClient(requestFactory))));
/// ```
public class ConfigurableAuthorizationCodeTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final RestClientAuthorizationCodeTokenResponseClient inner;

    /// @param httpRequestFactory the factory the token endpoint is called with
    /// @throws IllegalArgumentException when no supported json library is on the classpath
    public ConfigurableAuthorizationCodeTokenResponseClient(ClientHttpRequestFactory httpRequestFactory) {
	final var restClient = RestClient.builder()
                	.configureMessageConverters((messageConverters) -> {
				messageConverters.addCustomConverter(new FormHttpMessageConverter());
				messageConverters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
			})
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                        .requestFactory(httpRequestFactory)
			.build();
        this.inner = new RestClientAuthorizationCodeTokenResponseClient();
        this.inner.setRestClient(restClient);
    }

    /// @param authorizationGrantRequest the code to exchange, and the registration it is for
    /// @return the token response
    /// @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException when the
    /// exchange fails, carrying the identity provider's error code when it sent one
    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        return inner.getTokenResponse(authorizationGrantRequest);
    }

}
