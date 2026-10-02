package net.optionfactory.spring.upstream.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OauthClientGrantsTest {

    private static final class RecordingOauthClient implements OauthClient {

        public final Map<String, Object> params = new HashMap<>();
        public final Map<String, Object> headers = new HashMap<>();

        private static JsonNode token() {
            return JsonMapper.builder().build().readTree("{\"access_token\":\"t\",\"expires_in\":600}");
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params, Map<String, ?> headers) {
            this.params.putAll(params);
            this.headers.putAll(headers);
            return token();
        }

        @Override
        public JsonNode authenticate(MultiValueMap<String, ?> params, MultiValueMap<String, ?> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params) {
            this.params.putAll(params);
            return token();
        }

        @Override
        public JsonNode authenticate(MultiValueMap<String, ?> params) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void clientCredentialsAuthenticateWithBasicAuth() {
        final var client = new RecordingOauthClient();
        client.clientCredentials("id", "secret", "read write");
        final var expected = "Basic " + Base64.getEncoder().encodeToString("id:secret".getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(expected, client.headers.get(HttpHeaders.AUTHORIZATION), "client credentials must be sent as basic auth");
        Assertions.assertEquals(Map.of("grant_type", "client_credentials", "scope", "read write"), client.params, "the client credentials grant must only send grant_type and scope");
    }

    @Test
    public void clientCredentialsOmitAMissingScope() {
        final var client = new RecordingOauthClient();
        client.clientCredentials("id", "secret", null);
        Assertions.assertEquals(Map.of("grant_type", "client_credentials"), client.params, "a null scope must not be sent");
    }

    @Test
    public void passwordGrantOmitsMissingClientCredentials() {
        final var client = new RecordingOauthClient();
        client.password(null, null, "user", "pass");
        Assertions.assertEquals(Map.of("grant_type", "password", "username", "user", "password", "pass"), client.params, "null client credentials must not be sent");
    }

    @Test
    public void authorizationCodeGrantSendsOnlyTheGivenParameters() {
        final var client = new RecordingOauthClient();
        client.authorizationCode("code", "https://app.example.com/cb", "id", null, "verifier");
        Assertions.assertEquals(Map.of(
                "grant_type", "authorization_code",
                "code", "code",
                "redirect_uri", "https://app.example.com/cb",
                "client_id", "id",
                "code_verifier", "verifier"
        ), client.params, "the authorization code grant must send the non null parameters only");
    }

    @Test
    public void jwtBearerGrantSendsTheAssertion() {
        final var client = new RecordingOauthClient();
        client.jwtBearer("signed.assertion.value");
        Assertions.assertEquals(Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion", "signed.assertion.value"
        ), client.params, "the jwt bearer grant must send the RFC 7523 grant type and the assertion");
    }

    @Test
    public void clientCredentialsAuthenticatorSendsTheConfiguredScope() {
        final var client = new RecordingOauthClient();
        final var authenticator = OauthClientCredentialsAuthenticator.builder(client).clientId("id").clientSecret("secret").scope("read").build();
        final var request = new org.springframework.mock.http.client.MockClientHttpRequest();
        authenticator.initialize(null, request);
        Assertions.assertEquals("read", client.params.get("scope"), "the configured scope must be requested");
        Assertions.assertEquals("Bearer t", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), "the granted token must be sent as a bearer token");
    }
}
