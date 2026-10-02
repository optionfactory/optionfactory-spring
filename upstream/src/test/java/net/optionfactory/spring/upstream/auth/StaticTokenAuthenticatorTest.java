package net.optionfactory.spring.upstream.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;

public class StaticTokenAuthenticatorTest {

    private static MockClientHttpRequest initialized(StaticTokenAuthenticator authenticator) {
        final var request = new MockClientHttpRequest();
        authenticator.initialize(null, request);
        return request;
    }

    @Test
    public void bearerSetsTheAuthorizationHeader() {
        Assertions.assertEquals("Bearer abc", initialized(StaticTokenAuthenticator.bearer("abc")).getHeaders().getFirst(HttpHeaders.AUTHORIZATION), "the token must be sent with the Bearer scheme");
    }

    @Test
    public void basicEncodesTheCredentialsWithTheGivenCharset() {
        final var request = initialized(StaticTokenAuthenticator.basic("usèr", "pass", StandardCharsets.ISO_8859_1));
        final var expected = "Basic " + Base64.getEncoder().encodeToString("usèr:pass".getBytes(StandardCharsets.ISO_8859_1));
        Assertions.assertEquals(expected, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), "basic credentials must be base64 encoded in the given charset");
    }

    @Test
    public void proxyBasicTargetsTheProxyAuthorizationHeader() {
        final var request = initialized(StaticTokenAuthenticator.proxyBasic("user", "pass", StandardCharsets.UTF_8));
        Assertions.assertTrue(request.getHeaders().getFirst(HttpHeaders.PROXY_AUTHORIZATION).startsWith("Basic "), "proxy credentials must go in Proxy-Authorization");
        Assertions.assertFalse(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION), "proxy credentials must not leak into Authorization");
    }

    @Test
    public void customHeaderAndScheme() {
        final var request = initialized(new StaticTokenAuthenticator("X-Api-Key", "Key", "abc"));
        Assertions.assertEquals("Key abc", request.getHeaders().getFirst("X-Api-Key"), "the header must be scheme, space, token");
    }

    @Test
    public void existingHeaderIsReplaced() {
        final var request = new MockClientHttpRequest();
        request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer stale");
        StaticTokenAuthenticator.authorization("Token", "fresh").initialize(null, request);
        Assertions.assertEquals(java.util.List.of("Token fresh"), request.getHeaders().get(HttpHeaders.AUTHORIZATION), "the header must be set, not added");
    }
}
