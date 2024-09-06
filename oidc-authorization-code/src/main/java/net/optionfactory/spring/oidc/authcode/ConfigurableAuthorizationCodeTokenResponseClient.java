package net.optionfactory.spring.oidc.authcode;

import java.util.List;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

public class ConfigurableAuthorizationCodeTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final DefaultAuthorizationCodeTokenResponseClient inner;

    public ConfigurableAuthorizationCodeTokenResponseClient(ClientHttpRequestFactory httpRequestFactory) {
        final var accessTokenRestTemplate = new RestTemplate(List.of(new FormHttpMessageConverter(), new OAuth2AccessTokenResponseHttpMessageConverter()));
        accessTokenRestTemplate.setRequestFactory(httpRequestFactory);
        this.inner = new DefaultAuthorizationCodeTokenResponseClient();
        this.inner.setRestOperations(accessTokenRestTemplate);
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        return inner.getTokenResponse(authorizationGrantRequest);
    }

}
