package net.optionfactory.spring.authentication.resource.server;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class JwtTokenResolverAdapterTest {

    private static final byte[] HS256_KEY = HexFormat.of().parseHex("7465737400000000000000000000000000000000000000000000000000000000");

    private static String signedHs256Jws() {
        try {
            return signedJws(JWSAlgorithm.HS256, new MACSigner(HS256_KEY));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String signedRs256Jws() {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            final KeyPair keys = generator.generateKeyPair();
            return signedJws(JWSAlgorithm.RS256, new RSASSASigner(keys.getPrivate()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String signedJws(JWSAlgorithm algorithm, com.nimbusds.jose.JWSSigner signer) {
        try {
            final var jws = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), new JWTClaimsSet.Builder().build());
            jws.sign(signer);
            return jws.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JwtTokenResolverAdapter hs256Only() {
        return new JwtTokenResolverAdapter(header -> header.getAlgorithm() == JWSAlgorithm.HS256);
    }

    @Test
    public void matchingBearerJwtIsResolved() {
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", String.format("Bearer %s", signedHs256Jws()));
        Assertions.assertNotNull(hs256Only().resolve(request));
    }

    @Test
    public void lowercaseSchemeIsAccepted() {
        final var token = signedHs256Jws();
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", String.format("bearer %s", token));
        Assertions.assertEquals(token, hs256Only().resolve(request));
    }

    @Test
    public void jwtWithUnexpectedHeaderIsNotResolved() {
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", String.format("Bearer %s", signedRs256Jws()));
        Assertions.assertNull(hs256Only().resolve(request));
    }

    @Test
    public void nonJwtBearerTokenIsNotResolved() {
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-jwt");
        Assertions.assertNull(hs256Only().resolve(request));
    }

    @Test
    public void missingOrNonBearerAuthorizationIsNotResolved() {
        final var missing = new MockHttpServletRequest();
        Assertions.assertNull(hs256Only().resolve(missing));

        final var basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", String.format("Basic %s", signedHs256Jws()));
        Assertions.assertNull(hs256Only().resolve(basic));

        final var otherHeader = new MockHttpServletRequest();
        otherHeader.addHeader("X-Service-Token", signedHs256Jws());
        Assertions.assertNull(hs256Only().resolve(otherHeader));
    }

    @Test
    public void bearerSchemeWithoutTokenIsNotResolved() {
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        Assertions.assertNull(hs256Only().resolve(request));
    }
}
