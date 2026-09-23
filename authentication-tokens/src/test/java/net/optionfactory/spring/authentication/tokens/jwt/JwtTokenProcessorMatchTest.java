package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JwsProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;

public class JwtTokenProcessorMatchTest {

    private static final byte[] KEY_A = "a-shared-key-of-at-least-32-bytes".getBytes();
    private static final byte[] KEY_B = "b-shared-key-of-at-least-32-bytes".getBytes();
    private static final byte[] KEY_C = "c-shared-key-of-at-least-32-bytes".getBytes();

    private final HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "Bearer");

    @Test
    public void laxProcessorsOnOneHeaderEachAcceptTheirOwnTokens() throws Exception {
        final var processor = new JwtTokenProcessor(List.of(
                jws(ClaimsPolicy.permissive(), KEY_A, Match.LAX, "a"),
                jws(ClaimsPolicy.permissive(), KEY_B, Match.LAX, "b")
        ), List.of());

        Assertions.assertEquals("a", processor.process(hs, signed(KEY_A, claims())).principal());
        Assertions.assertEquals("b", processor.process(hs, signed(KEY_B, claims())).principal());
    }

    @Test
    public void aTokenNoLaxProcessorAcceptsIsNotAuthenticated() throws Exception {
        final var processor = new JwtTokenProcessor(List.of(
                jws(ClaimsPolicy.permissive(), KEY_A, Match.LAX, "a"),
                jws(ClaimsPolicy.permissive(), KEY_B, Match.LAX, "b")
        ), List.of());

        Assertions.assertNull(processor.process(hs, signed(KEY_C, claims())));
    }

    @Test
    public void aLaxProcessorPassesOnATokenFailingItsClaims() throws Exception {
        final var processor = new JwtTokenProcessor(List.of(
                jws(ClaimsPolicy.audience("service-a"), KEY_A, Match.LAX, "a"),
                jws(ClaimsPolicy.audience("service-b"), KEY_A, Match.LAX, "b")
        ), List.of());

        final var token = signed(KEY_A, claims().audience("service-b").expirationTime(Date.from(Instant.now().plusSeconds(60))));

        Assertions.assertEquals("b", processor.process(hs, token).principal());
    }

    @Test
    public void aStrictProcessorKeepsATokenItRejectsFromLaterProcessors() throws Exception {
        final var processor = new JwtTokenProcessor(List.of(
                jws(ClaimsPolicy.permissive(), KEY_A, Match.STRICT, "a"),
                jws(ClaimsPolicy.permissive(), KEY_B, Match.LAX, "b")
        ), List.of());

        final var token = signed(KEY_B, claims());

        Assertions.assertThrows(BadCredentialsException.class, () -> processor.process(hs, token));
    }

    @Test
    public void strictProcessorsCanBeRoutedByTheUnverifiedIssuer() throws Exception {
        final var processor = new JwtTokenProcessor(List.of(
                jws(ClaimsPolicy.issuer("issuer-a"), KEY_A, byIssuer("issuer-a"), "a"),
                jws(ClaimsPolicy.issuer("issuer-b"), KEY_B, byIssuer("issuer-b"), "b")
        ), List.of());

        final var token = signed(KEY_B, claims().issuer("issuer-b").expirationTime(Date.from(Instant.now().plusSeconds(60))));

        Assertions.assertEquals("b", processor.process(hs, token).principal());
    }

    @Test
    public void laxJweProcessorsSharingADecrypterEachAcceptTheirOwnIssuersTokens() throws Exception {
        final var recipient = new ECKeyGenerator(Curve.P_256).generate();
        final var issuerA = new ECKeyGenerator(Curve.P_256).generate();
        final var issuerB = new ECKeyGenerator(Curve.P_256).generate();
        final var processor = new JwtTokenProcessor(List.of(), List.of(
                jwe(recipient, issuerA, "a"),
                jwe(recipient, issuerB, "b")
        ));

        final PrincipalAndAuthorities result = processor.process(hs, nested(recipient, issuerB));

        Assertions.assertEquals("b", result.principal());
    }

    private static JwsMatcher byIssuer(String issuer) {
        return (header, unverifiedClaims, jws) -> issuer.equals(unverifiedClaims.getIssuer()) ? Match.STRICT : Match.SKIP;
    }

    private static JwsProcessor jws(ClaimsPolicy claims, byte[] key, Match match, String principal) {
        return jws(claims, key, (header, unverifiedClaims, jws) -> match, principal);
    }

    private static JwsProcessor jws(ClaimsPolicy claims, byte[] key, JwsMatcher matcher, String principal) {
        final var b = JwsAuthenticationConfigurer.builder(claims);
        b.matchToken(matcher);
        b.verify(key);
        b.principal(principal);
        return b.build();
    }

    private static JweProcessor jwe(ECKey recipient, ECKey issuer, String principal) throws Exception {
        final var b = JweAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.match(Match.LAX);
        b.decrypter(new ECDHDecrypter(recipient.toECPrivateKey()));
        b.verify(issuer.toECPublicKey());
        b.principal(principal);
        return b.build();
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("someone").issueTime(Date.from(Instant.now()));
    }

    private static String signed(byte[] key, JWTClaimsSet.Builder claims) throws Exception {
        final var jws = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jws.sign(new MACSigner(key));
        return jws.serialize();
    }

    private static String nested(ECKey recipient, ECKey issuer) throws Exception {
        final var inner = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims().build());
        inner.sign(new ECDSASigner(issuer.toECPrivateKey()));
        final var jwe = new JWEObject(new JWEHeader(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM), new Payload(inner.serialize()));
        jwe.encrypt(new ECDHEncrypter(recipient.toECPublicKey()));
        return jwe.serialize();
    }
}
