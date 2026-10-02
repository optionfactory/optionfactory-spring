package net.optionfactory.spring.upstream.jws;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.io.OutputStream;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.upstream.auth.OauthClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class UpstreamJwtBearerGrantAuthenticatorTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    static KeyPair generateRsaKeyPair() {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static class MutableClock extends Clock {

        private Instant now;

        public MutableClock(Instant now) {
            this.now = now;
        }

        public void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
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

    private static class RecordingOauthClient implements OauthClient {

        public final List<Map<String, ?>> requests = new ArrayList<>();
        public boolean includeAccessToken = true;
        private final JsonMapper mapper = JsonMapper.builder().build();
        private long sequence = 0;

        private ObjectNode tokenResponse() {
            sequence++;
            final var node = mapper.createObjectNode();
            if (includeAccessToken) {
                node.put("access_token", String.format("token-%d", sequence));
            }
            node.put("token_type", "Bearer");
            node.put("expires_in", 600);
            return node;
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params) {
            requests.add(params);
            return tokenResponse();
        }

        @Override
        public JsonNode authenticate(Map<String, ?> params, Map<String, ?> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonNode authenticate(MultiValueMap<String, ?> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonNode authenticate(MultiValueMap<String, ?> params, MultiValueMap<String, ?> headers) {
            throw new UnsupportedOperationException();
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

    private final KeyPair keys = generateRsaKeyPair();
    private final MutableClock clock = new MutableClock(START);
    private final RecordingOauthClient oauth = new RecordingOauthClient();

    private UpstreamJwtBearerGrantAuthenticator authenticator() {
        return UpstreamJwtBearerGrantAuthenticator.builder(oauth, new RSASSASigner(keys.getPrivate()))
                .issuer("sa@project.iam.gserviceaccount.com")
                .audience("https://oauth2.googleapis.com/token")
                .scopes("https://www.googleapis.com/auth/firebase.messaging")
                .clock(clock)
                .build();
    }

    @Test
    public void initializationSetsBearerHeaderFromExchangedToken() {
        final var authenticator = authenticator();
        final var request = new StubClientHttpRequest();
        authenticator.initialize(null, request);
        Assertions.assertEquals("Bearer token-1", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), "the exchanged access token is sent as a bearer token");
        Assertions.assertEquals(1, oauth.requests.size(), "one exchange for the first request");
    }

    @Test
    public void exchangeCarriesJwtBearerGrantAndSignedAssertion() throws Exception {
        final var authenticator = authenticator();
        authenticator.accessToken();
        Assertions.assertEquals(1, oauth.requests.size(), "one exchange per access token");
        final var params = oauth.requests.get(0);
        Assertions.assertEquals(UpstreamJwtBearerGrantAuthenticator.GRANT_TYPE, params.get("grant_type"), "the exchange uses the jwt-bearer grant");
        final var assertion = (String) params.get("assertion");
        final var jwt = SignedJWT.parse(assertion);
        Assertions.assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm(), "the assertion is signed with the configured algorithm");
        Assertions.assertEquals(JOSEObjectType.JWT, jwt.getHeader().getType(), "the assertion declares a JWT type");
        Assertions.assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())), "the assertion verifies with the public key of the signing key");
        final var claims = jwt.getJWTClaimsSet();
        Assertions.assertEquals("sa@project.iam.gserviceaccount.com", claims.getIssuer(), "iss is the configured issuer");
        Assertions.assertEquals(claims.getIssuer(), claims.getSubject(), "sub defaults to the issuer");
        Assertions.assertEquals(List.of("https://oauth2.googleapis.com/token"), claims.getAudience(), "aud is the configured audience");
        Assertions.assertEquals(START, claims.getIssueTime().toInstant(), "iat is the current instant");
        Assertions.assertEquals(START.plus(Duration.ofHours(1)), claims.getExpirationTime().toInstant(), "the assertion expires one hour after issue");
        Assertions.assertFalse(claims.getJWTID().isBlank(), "every assertion carries a jti");
        Assertions.assertEquals("https://www.googleapis.com/auth/firebase.messaging", claims.getStringClaim("scope"), "the configured scope is carried in the scope claim");
    }

    @Test
    public void configuredSubjectOverridesIssuer() throws Exception {
        final var authenticator = UpstreamJwtBearerGrantAuthenticator.builder(oauth, new RSASSASigner(keys.getPrivate()))
                .issuer("sa@project.iam.gserviceaccount.com")
                .subject("impersonated@project.iam.gserviceaccount.com")
                .audience("https://oauth2.googleapis.com/token")
                .clock(clock)
                .build();
        authenticator.accessToken();
        final var assertion = (String) oauth.requests.get(0).get("assertion");
        final var claims = SignedJWT.parse(assertion).getJWTClaimsSet();
        Assertions.assertEquals("impersonated@project.iam.gserviceaccount.com", claims.getSubject(), "a configured subject overrides the issuer as sub");
        Assertions.assertNull(claims.getStringClaim("scope"), "no scope claim when no scope is configured");
    }

    @Test
    public void missingAccessTokenInTokenResponseFails() {
        oauth.includeAccessToken = false;
        final var authenticator = authenticator();
        Assertions.assertThrows(IllegalStateException.class, authenticator::accessToken, "a token response without access_token fails the exchange");
    }

    @Test
    public void multipleScopesAreSpaceSeparatedInScopeClaim() throws Exception {
        final var authenticator = UpstreamJwtBearerGrantAuthenticator.builder(oauth, new RSASSASigner(keys.getPrivate()))
                .issuer("sa@project.iam.gserviceaccount.com")
                .audience("https://oauth2.googleapis.com/token")
                .scopes("scope-a", "scope-b")
                .clock(clock)
                .build();
        authenticator.accessToken();
        final var assertion = (String) oauth.requests.get(0).get("assertion");
        final var claims = SignedJWT.parse(assertion).getJWTClaimsSet();
        Assertions.assertEquals("scope-a scope-b", claims.getStringClaim("scope"), "multiple scopes are joined by spaces");
    }

    /// `expires_in` is 600s and the default refresh margin is 60s: a refresh is expected at
    /// START+540s.
    @Test
    public void accessTokenIsCachedUntilExpiryMinusMargin() {
        final var authenticator = authenticator();
        Assertions.assertEquals("token-1", authenticator.accessToken(), "the first call exchanges a token");
        clock.set(START.plusSeconds(500));
        Assertions.assertEquals("token-1", authenticator.accessToken(), "the cached token is reused before the refresh margin");
        Assertions.assertEquals(1, oauth.requests.size(), "no exchange while the cached token is fresh");
        clock.set(START.plusSeconds(541));
        Assertions.assertEquals("token-2", authenticator.accessToken(), "a new token is exchanged once within the refresh margin");
        Assertions.assertEquals(2, oauth.requests.size(), "one more exchange after the refresh margin");
    }

}
