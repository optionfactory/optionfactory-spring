package net.optionfactory.spring.authentication.code;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

public class ConfigurableOidcIdTokenDecoderFactoryTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String CLIENT_SECRET = "a-client-secret-of-at-least-32-bytes";
    private static ECKey issuerKey;
    private static ECKey anotherKey;

    @BeforeAll
    public static void generateKeys() throws Exception {
        issuerKey = new ECKeyGenerator(Curve.P_256).keyID("issuer-key").generate();
        anotherKey = new ECKeyGenerator(Curve.P_256).keyID("issuer-key").generate();
    }

    private static ClientRegistration.Builder registration() {
        return ClientRegistration.withRegistrationId("idp")
                .clientId("client-id")
                .clientSecret(CLIENT_SECRET)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example.com/login/oauth2/code/idp")
                .authorizationUri(ISSUER + "/authorize")
                .tokenUri(ISSUER + "/token")
                .issuerUri(ISSUER)
                .scope("openid");
    }

    private static JWTClaimsSet.Builder idTokenClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("alice")
                .audience("client-id")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String signed(JWSAlgorithm algorithm, JWSSigner signer, JWTClaimsSet claims) throws Exception {
        final var jws = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("issuer-key").build(), claims);
        jws.sign(signer);
        return jws.serialize();
    }

    private static ConfigurableOidcIdTokenDecoderFactory es256(StubClientHttpRequestFactory jwks) {
        final var factory = new ConfigurableOidcIdTokenDecoderFactory(jwks);
        factory.setJwsAlgorithmResolver(registration -> SignatureAlgorithm.ES256);
        return factory;
    }

    private static StubClientHttpRequestFactory jwks() {
        return new StubClientHttpRequestFactory(HttpStatus.OK, new JWKSet(issuerKey.toPublicJWK()).toString());
    }

    @Test
    public void theJwkSetIsFetchedThroughTheConfiguredRequestFactory() throws Exception {
        final var jwks = jwks();
        final var decoder = es256(jwks).createDecoder(registration().jwkSetUri(ISSUER + "/jwks").build());

        final var jwt = decoder.decode(signed(JWSAlgorithm.ES256, new ECDSASigner(issuerKey), idTokenClaims().build()));

        Assertions.assertEquals("alice", jwt.getSubject(), "an id token signed by a key in the issuer's jwk set is decoded");
        Assertions.assertFalse(jwks.requests.isEmpty(), "the jwk set is fetched through the configured request factory");
        Assertions.assertEquals(ISSUER + "/jwks", jwks.requests.get(0).getURI().toString(), "the jwk set is fetched from the registration's jwk set uri");
    }

    @Test
    public void anIdTokenSignedWithAnotherKeyIsRejected() throws Exception {
        final var decoder = es256(jwks()).createDecoder(registration().jwkSetUri(ISSUER + "/jwks").build());
        final var forged = signed(JWSAlgorithm.ES256, new ECDSASigner(anotherKey), idTokenClaims().build());

        Assertions.assertThrows(JwtException.class, () -> decoder.decode(forged), "an id token not signed by the issuer's key is rejected");
    }

    @Test
    public void anIdTokenForAnotherClientIsRejected() throws Exception {
        final var decoder = es256(jwks()).createDecoder(registration().jwkSetUri(ISSUER + "/jwks").build());
        final var token = signed(JWSAlgorithm.ES256, new ECDSASigner(issuerKey), idTokenClaims().audience("another-client").build());

        Assertions.assertThrows(JwtValidationException.class, () -> decoder.decode(token), "an id token whose aud does not name this client is rejected");
    }

    @Test
    public void anIdTokenFromAnotherIssuerIsRejected() throws Exception {
        final var decoder = es256(jwks()).createDecoder(registration().jwkSetUri(ISSUER + "/jwks").build());
        final var token = signed(JWSAlgorithm.ES256, new ECDSASigner(issuerKey), idTokenClaims().issuer("https://elsewhere.example.com").build());

        Assertions.assertThrows(JwtValidationException.class, () -> decoder.decode(token), "an id token whose iss is not the registration's issuer is rejected");
    }

    @Test
    public void macAlgorithmsVerifyWithTheClientSecret() throws Exception {
        final var factory = new ConfigurableOidcIdTokenDecoderFactory(jwks());
        factory.setJwsAlgorithmResolver(registration -> MacAlgorithm.HS256);
        final var decoder = factory.createDecoder(registration().build());

        final var jwt = decoder.decode(signed(JWSAlgorithm.HS256, new MACSigner(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8)), idTokenClaims().build()));

        Assertions.assertEquals("alice", jwt.getSubject(), "an id token mac-ed with the client secret is decoded");
    }

    @Test
    public void aSignatureAlgorithmWithoutAJwkSetUriIsRefused() {
        final var factory = new ConfigurableOidcIdTokenDecoderFactory(jwks());
        final var registration = registration().build();

        final var thrown = Assertions.assertThrows(OAuth2AuthenticationException.class, () -> factory.createDecoder(registration), "rs256, the default, needs the issuer's jwk set");
        Assertions.assertEquals("missing_signature_verifier", thrown.getError().getErrorCode(), "the error says no verifier could be made");
    }

    @Test
    public void aMacAlgorithmWithoutAClientSecretIsRefused() {
        final var factory = new ConfigurableOidcIdTokenDecoderFactory(jwks());
        factory.setJwsAlgorithmResolver(registration -> MacAlgorithm.HS256);
        final var registration = registration().clientSecret(null).build();

        final var thrown = Assertions.assertThrows(OAuth2AuthenticationException.class, () -> factory.createDecoder(registration), "a mac algorithm needs the client secret");
        Assertions.assertEquals("missing_signature_verifier", thrown.getError().getErrorCode(), "the error says no verifier could be made");
    }

    @Test
    public void noAlgorithmIsRefused() {
        final var factory = new ConfigurableOidcIdTokenDecoderFactory(jwks());
        factory.setJwsAlgorithmResolver(registration -> null);
        final var registration = registration().jwkSetUri(ISSUER + "/jwks").build();

        Assertions.assertThrows(OAuth2AuthenticationException.class, () -> factory.createDecoder(registration), "a resolver returning no algorithm leaves no way to verify the id token");
    }

    @Test
    public void decodersAreCachedPerRegistrationId() {
        final var factory = es256(jwks());
        final var first = factory.createDecoder(registration().jwkSetUri(ISSUER + "/jwks").build());
        final var second = factory.createDecoder(registration().jwkSetUri(ISSUER + "/another-jwks").build());

        Assertions.assertSame(first, second, "the decoder made for a registration id is reused, even for a changed registration");
    }
}
