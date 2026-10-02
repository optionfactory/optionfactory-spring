package net.optionfactory.spring.authentication.code;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

public class ConfigurableAuthorizationCodeTokenResponseClientTest {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("idp")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://app.example.com/login/oauth2/code/idp")
            .authorizationUri("https://idp.example.com/authorize")
            .tokenUri("https://idp.example.com/token")
            .scope("openid")
            .build();

    private static OAuth2AuthorizationCodeGrantRequest grant() {
        final var request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(REGISTRATION.getProviderDetails().getAuthorizationUri())
                .clientId(REGISTRATION.getClientId())
                .redirectUri(REGISTRATION.getRedirectUri())
                .state("state")
                .build();
        final var response = OAuth2AuthorizationResponse.success("the-code")
                .redirectUri(REGISTRATION.getRedirectUri())
                .state("state")
                .build();
        return new OAuth2AuthorizationCodeGrantRequest(REGISTRATION, new OAuth2AuthorizationExchange(request, response));
    }

    @Test
    public void theCodeIsExchangedThroughTheConfiguredRequestFactory() throws Exception {
        final var idp = new StubClientHttpRequestFactory(HttpStatus.OK, """
                {"access_token": "the-access-token", "token_type": "Bearer", "expires_in": 300}
                """);

        final var response = new ConfigurableAuthorizationCodeTokenResponseClient(idp).getTokenResponse(grant());

        Assertions.assertEquals("the-access-token", response.getAccessToken().getTokenValue(), "the token response is parsed from the identity provider's answer");
        Assertions.assertEquals(1, idp.requests.size(), "the token endpoint is called once, through the configured request factory");
        final var call = idp.requests.get(0);
        Assertions.assertEquals(HttpMethod.POST, call.getMethod(), "the token endpoint is called with a POST");
        Assertions.assertEquals("https://idp.example.com/token", call.getURI().toString(), "the token endpoint is the registration's");
        final var form = call.getBodyAsString();
        Assertions.assertTrue(form.contains("grant_type=authorization_code"), "the form carries the authorization code grant type");
        Assertions.assertTrue(form.contains("code=the-code"), "the form carries the code received by the redirect");
    }

    @Test
    public void anErrorResponseSurfacesTheIdentityProvidersErrorCode() {
        final var idp = new StubClientHttpRequestFactory(HttpStatus.BAD_REQUEST, """
                {"error": "invalid_grant", "error_description": "code expired"}
                """);
        final var client = new ConfigurableAuthorizationCodeTokenResponseClient(idp);

        final var thrown = Assertions.assertThrows(OAuth2AuthorizationException.class, () -> client.getTokenResponse(grant()), "a rejected exchange fails the login");
        Assertions.assertEquals("invalid_grant", thrown.getError().getErrorCode(), "the identity provider's error code is kept");
    }
}
