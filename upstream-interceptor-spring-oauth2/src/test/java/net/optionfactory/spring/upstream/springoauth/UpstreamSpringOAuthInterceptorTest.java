package net.optionfactory.spring.upstream.springoauth;

import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

public class UpstreamSpringOAuthInterceptorTest {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("upstream")
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("https://auth.example.com/token")
            .build();

    private static class RecordingAuthorizedClientManager implements OAuth2AuthorizedClientManager {

        public final List<OAuth2AuthorizeRequest> requests = new ArrayList<>();
        public OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", Instant.now(), Instant.now().plusSeconds(600));

        @Override
        public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest authorizeRequest) {
            requests.add(authorizeRequest);
            return new OAuth2AuthorizedClient(REGISTRATION, "upstream-principal", token);
        }

    }

    static class StubClientHttpRequest implements ClientHttpRequest {

        public final HttpHeaders headers = new HttpHeaders();

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.POST;
        }

        @Override
        public URI getURI() {
            return URI.create("https://api.example.com/resource");
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public OutputStream getBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public ClientHttpResponse execute() {
            throw new UnsupportedOperationException();
        }

    }

    @Test
    public void initializationSetsBearerHeaderFromAuthorizedClientToken() {
        final var oauth = new RecordingAuthorizedClientManager();
        final var authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("upstream")
                .principal("upstream-principal")
                .build();
        final var interceptor = new UpstreamSpringOAuthInterceptor(oauth, authorizeRequest);

        final var request = new StubClientHttpRequest();
        interceptor.initialize(null, request);

        Assertions.assertEquals("Bearer token-value", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals(1, oauth.requests.size());
        Assertions.assertSame(authorizeRequest, oauth.requests.get(0));
    }

    @Test
    public void expiredTokenIsStillAttachedVerbatim() {
        //token lifecycle is the manager's responsibility: whatever it returns is used as-is
        final var oauth = new RecordingAuthorizedClientManager();
        oauth.token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "stale-token", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        final var interceptor = new UpstreamSpringOAuthInterceptor(oauth, OAuth2AuthorizeRequest.withClientRegistrationId("upstream").principal("upstream-principal").build());

        final var request = new StubClientHttpRequest();
        interceptor.initialize(null, request);

        Assertions.assertEquals("Bearer stale-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void missingAuthorizedClientFailsFastWithNpe() {
        final var interceptor = new UpstreamSpringOAuthInterceptor(authorizeRequest -> null, OAuth2AuthorizeRequest.withClientRegistrationId("upstream").principal("upstream-principal").build());
        final var request = new StubClientHttpRequest();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.initialize(null, request));
    }
}
