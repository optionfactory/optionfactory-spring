package net.optionfactory.spring.upstream.auth;

import java.io.OutputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OauthAuthenticatorsCachingTest {

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

    private static final class StubOauthClient implements OauthClient {

        public int grants = 0;
        public final List<Map<String, ?>> lastParams = new ArrayList<>();
        private String response = "{\"access_token\":\"t0\",\"expires_in\":600}";

        public void respondWith(String response) {
            this.response = response;
        }

        private JsonNode token() {
            grants++;
            return JsonMapper.builder().build().readTree(response);
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params, Map<String, ?> headers) {
            lastParams.add(params);
            return token();
        }

        @Override
        public JsonNode authenticate(org.springframework.util.MultiValueMap<String, ?> params, org.springframework.util.MultiValueMap<String, ?> headers) {
            return token();
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params) {
            lastParams.add(params);
            return token();
        }

        @Override
        public JsonNode authenticate(org.springframework.util.MultiValueMap<String, ?> params) {
            return token();
        }

    }

    private static final class RecordingRequest implements ClientHttpRequest {

        public final HttpHeaders headers = new HttpHeaders();

        @Override
        public OutputStream getBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create("http://example.com/resource");
        }

        @Override
        public ClientHttpResponse execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

    }

    @Test
    public void clientCredentialsGrantIsCachedUntilShortlyBeforeExpiry() {
        final var oauth = new StubOauthClient();
        final var clock = new MutableClock();
        final var authenticator = OauthClientCredentialsAuthenticator.builder(oauth)
                .clientId("id")
                .clientSecret("secret")
                .clock(clock)
                .build();
        final var first = new RecordingRequest();
        authenticator.initialize(null, first);
        final var second = new RecordingRequest();
        authenticator.initialize(null, second);
        Assertions.assertEquals(1, oauth.grants, "two requests in the same validity window must share one grant");
        Assertions.assertEquals("Bearer t0", first.headers.getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals("Bearer t0", second.headers.getFirst(HttpHeaders.AUTHORIZATION));

        clock.advanceSeconds(539);
        final var stillCached = new RecordingRequest();
        authenticator.initialize(null, stillCached);
        Assertions.assertEquals(1, oauth.grants, "refresh margin is 60s: a 600s token must still be cached at t+539s");

        clock.advanceSeconds(2);
        oauth.respondWith("{\"access_token\":\"t1\",\"expires_in\":600}");
        final var refreshed = new RecordingRequest();
        authenticator.initialize(null, refreshed);
        Assertions.assertEquals(2, oauth.grants, "the cached token must be refreshed once past the refresh point");
        Assertions.assertEquals("Bearer t1", refreshed.headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void tokenResponseWithoutAccessTokenFailsFast() {
        final var oauth = new StubOauthClient();
        oauth.respondWith("{\"token_type\":\"bearer\"}");
        final var authenticator = OauthClientCredentialsAuthenticator.builder(oauth)
                .clientId("id")
                .clientSecret("secret")
                .build();
        Assertions.assertThrows(IllegalStateException.class, () -> authenticator.initialize(null, new RecordingRequest()));
    }

    @Test
    public void passwordGrantIsCachedAndSendsTheCredentials() {
        final var oauth = new StubOauthClient();
        final var authenticator = OauthPasswordAuthenticator.builder(oauth)
                .clientId("id")
                .clientSecret("secret")
                .username("user")
                .password("pass")
                .build();
        final var first = new RecordingRequest();
        authenticator.initialize(null, first);
        final var second = new RecordingRequest();
        authenticator.initialize(null, second);
        Assertions.assertEquals(1, oauth.grants);
        Assertions.assertEquals("Bearer t0", second.headers.getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals(Map.of(
                "grant_type", "password",
                "username", "user",
                "password", "pass",
                "client_id", "id",
                "client_secret", "secret"
        ), oauth.lastParams.get(0));
    }
}
