package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

public class JwtTokenProcessorRejectionTest {

    private static final byte[] KEY = "a-shared-key-of-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_32 = "an-aes-key-of-exactly-32-bytes!!".getBytes(StandardCharsets.UTF_8);
    private static final HeaderAndScheme BEARER = new HeaderAndScheme("Authorization", "Bearer");

    private static JwtTokenProcessor jws(Match match, JwtPrincipalConverter principal) {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.matchToken(match);
        b.verify(KEY);
        b.principal(principal);
        return new JwtTokenProcessor(List.of(b.build()), List.of());
    }

    private static JwtTokenProcessor jws(Match match) {
        return jws(match, (header, claims) -> claims.getSubject());
    }

    private static JwtTokenProcessor jwe(Match match, byte[] aesKey) {
        final var b = JweAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.match(match);
        b.decrypt(aesKey);
        b.principal((header, claims) -> claims.getSubject());
        return new JwtTokenProcessor(List.of(), List.of(b.build()));
    }

    private static JWTClaimsSet claims(String subject) {
        return new JWTClaimsSet.Builder().subject(subject).build();
    }

    private static String hs256(JWTClaimsSet claims) throws Exception {
        final var jws = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jws.sign(new MACSigner(KEY));
        return jws.serialize();
    }

    private static String a256kw(byte[] aesKey, JWTClaimsSet claims) throws Exception {
        final var jwe = new EncryptedJWT(new JWEHeader(JWEAlgorithm.A256KW, EncryptionMethod.A128GCM), claims);
        jwe.encrypt(new AESEncrypter(new SecretKeySpec(aesKey, "AES")));
        return jwe.serialize();
    }

    @Test
    public void aValidTokenIsAccepted() throws Exception {
        Assertions.assertEquals("alice", jws(Match.STRICT).process(BEARER, hs256(claims("alice"))).principal(), "a token signed with the configured key is accepted");
    }

    @Test
    public void anUnsecuredTokenIsNeverAccepted() {
        final var unsecured = new PlainJWT(claims("admin")).serialize();

        Assertions.assertNull(jws(Match.STRICT).process(BEARER, unsecured), "an alg:none token carries no signature and is never accepted");
    }

    @Test
    public void aTokenThatIsNotAJwtIsLeftToOtherProcessors() {
        Assertions.assertNull(jws(Match.STRICT).process(BEARER, "not-a-jwt"), "a static token is not for the jwt processor, even a strict one");
    }

    @Test
    public void aTokenWithATamperedPayloadIsRejected() throws Exception {
        final var parts = hs256(claims("alice")).split("\\.");
        final var forged = String.join(".", parts[0], Base64URL.encode(claims("admin").toString()).toString(), parts[2]);

        Assertions.assertThrows(BadCredentialsException.class, () -> jws(Match.STRICT).process(BEARER, forged), "a payload no longer matching its signature is rejected");
    }

    @Test
    public void aTokenSignedWithAnAlgorithmTheVerifierDoesNotSupportIsRejected() throws Exception {
        final var jws = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims("admin"));
        jws.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));

        Assertions.assertThrows(BadCredentialsException.class, () -> jws(Match.STRICT).process(BEARER, jws.serialize()), "the token cannot choose its own verification algorithm");
    }

    @Test
    public void aLaxProcessorLeavesATokenItRejectsUnauthenticated() throws Exception {
        final var signedElsewhere = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims("alice"));
        signedElsewhere.sign(new MACSigner("another-key-of-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertNull(jws(Match.LAX).process(BEARER, signedElsewhere.serialize()), "a lax processor passes on the tokens it rejects instead of failing them");
    }

    @Test
    public void aSkippingProcessorNeverAcceptsAToken() throws Exception {
        Assertions.assertNull(jws(Match.SKIP).process(BEARER, hs256(claims("alice"))), "a skipped token is not verified, so it cannot be accepted");
    }

    @Test
    public void aTokenOnAnotherHeaderIsNotConsidered() throws Exception {
        final var other = new HeaderAndScheme("X-Service-Token", "Bearer");

        Assertions.assertNull(jws(Match.STRICT).process(other, hs256(claims("alice"))), "a processor only considers tokens found on its own header and scheme");
    }

    @Test
    public void aNullPrincipalRejectsTheToken() throws Exception {
        final var processor = jws(Match.STRICT, (header, claims) -> null);

        Assertions.assertThrows(BadCredentialsException.class, () -> processor.process(BEARER, hs256(claims("alice"))), "a token no principal is derived from is rejected");
    }

    @Test
    public void anAuthoritiesConverterRejectingTheClaimsRejectsTheToken() throws Exception {
        final var token = hs256(new JWTClaimsSet.Builder().subject("alice").claim("roles", "ADMIN").build());

        Assertions.assertThrows(BadCredentialsException.class, () -> jws(Match.STRICT).process(BEARER, token), "a roles claim that is not a list of strings rejects the token");
    }

    @Test
    public void aJweEncryptedWithAnotherKeyIsRejected() throws Exception {
        final var token = a256kw("another-aes-key-of-exactly-32-by".getBytes(StandardCharsets.UTF_8), claims("alice"));

        Assertions.assertThrows(BadCredentialsException.class, () -> jwe(Match.STRICT, KEY_32).process(BEARER, token), "a strict jwe processor rejects a token it cannot decrypt");
        Assertions.assertNull(jwe(Match.LAX, KEY_32).process(BEARER, token), "a lax jwe processor passes on a token it cannot decrypt");
    }

    @Test
    public void aJweEncryptedWithTheSharedKeyIsAccepted() throws Exception {
        final var token = a256kw(KEY_32, claims("alice"));

        Assertions.assertEquals("alice", jwe(Match.STRICT, KEY_32).process(BEARER, token).principal(), "a symmetric jwe decrypted with the shared key is accepted");
    }
}
