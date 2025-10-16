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

public class ConfigurableAuthorizationCodeTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final RestClientAuthorizationCodeTokenResponseClient inner;

    public ConfigurableAuthorizationCodeTokenResponseClient(ClientHttpRequestFactory httpRequestFactory) {
	final var restClient = RestClient.builder()
			.messageConverters((messageConverters) -> {
				messageConverters.clear();
				messageConverters.add(new FormHttpMessageConverter());
				messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
			})
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                        .requestFactory(httpRequestFactory)
			.build();
        this.inner = new RestClientAuthorizationCodeTokenResponseClient();
        this.inner.setRestClient(restClient);
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        return inner.getTokenResponse(authorizationGrantRequest);
    }

}
