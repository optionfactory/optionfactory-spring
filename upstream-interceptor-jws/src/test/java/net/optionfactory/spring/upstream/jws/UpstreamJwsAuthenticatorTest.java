package net.optionfactory.spring.upstream.jws;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

public class UpstreamJwsAuthenticatorTest {

    @Test
    public void asymmetricSignerProducesVerifiableJwt() throws Exception {
        final var keys = UpstreamJwtBearerGrantAuthenticatorTest.generateRsaKeyPair();
        final var authenticator = new UpstreamJwsAuthenticator(
                "my-issuer",
                new RSASSASigner(keys.getPrivate()),
                "my-audience",
                Duration.ofMinutes(5),
                ctx -> "custom-subject",
                JWSAlgorithm.RS256);
        final var request = new UpstreamJwtBearerGrantAuthenticatorTest.StubClientHttpRequest();
        authenticator.initialize(null, request);
        final var authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Assertions.assertTrue(authorization.startsWith("Bearer "));
        final var jwt = SignedJWT.parse(authorization.substring("Bearer ".length()));
        Assertions.assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        Assertions.assertEquals(JOSEObjectType.JWT, jwt.getHeader().getType());
        Assertions.assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())));
        final var claims = jwt.getJWTClaimsSet();
        Assertions.assertEquals("my-issuer", claims.getIssuer());
        Assertions.assertEquals("custom-subject", claims.getSubject());
        Assertions.assertEquals(List.of("my-audience"), claims.getAudience());
    }

}
