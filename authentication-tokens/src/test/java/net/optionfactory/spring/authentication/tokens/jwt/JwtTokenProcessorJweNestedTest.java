package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.jwt.JwtTokenProcessor.JweProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;

public class JwtTokenProcessorJweNestedTest {

    private JwtTokenProcessor processor;
    private ECPublicKey issuerPublic;
    private ECPrivateKey issuerPrivate;
    private ECPublicKey recipientPublic;
    private ECPrivateKey recipientPrivate;
    private final HeaderAndScheme hs = new HeaderAndScheme(HttpHeaders.AUTHORIZATION, "BEARER ");

    @BeforeEach
    public void setUp() throws Exception {
        final var issuerKey = new ECKeyGenerator(Curve.P_256).generate();
        this.issuerPublic = issuerKey.toECPublicKey();
        this.issuerPrivate = issuerKey.toECPrivateKey();
        final var recipientKey = new ECKeyGenerator(Curve.P_256).generate();
        this.recipientPublic = recipientKey.toECPublicKey();
        this.recipientPrivate = recipientKey.toECPrivateKey();

        final var b = JweAuthenticationConfigurer.builder();
        b.matchHeader(HttpHeaders.AUTHORIZATION, "Bearer");
        b.matchToken((header, jwe) -> Match.STRICT);
        b.decrypter(new ECDHDecrypter(recipientPrivate));
        b.verify(issuerPublic);
        b.principal((header, claims) -> claims.getSubject());
        b.authorities(new RolesGroupsAndScopesFromClaims(List.of()));
        final JweProcessor jweProc = b.build();
        this.processor = new JwtTokenProcessor(List.of(), List.of(jweProc));
    }

    private String nestedJwe(ECPrivateKey signingKey, JWTClaimsSet claims) throws Exception {
        final var inner = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        inner.sign(new ECDSASigner(signingKey));
        final var jwe = new JWEObject(new JWEHeader(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM), new Payload(inner.serialize()));
        jwe.encrypt(new ECDHEncrypter(recipientPublic));
        return jwe.serialize();
    }

    private String rawClaimsJwe(JWTClaimsSet claims) throws Exception {
        final var jwe = new EncryptedJWT(new JWEHeader(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM), claims);
        jwe.encrypt(new ECDHEncrypter(recipientPublic));
        return jwe.serialize();
    }

    @Test
    public void symmetricRawClaimsJweIsAcceptedWithoutInnerSignature() throws Exception {
        // 256-bit shared secret is the trust root; raw claims (no inner JWS) are the intended symmetric mode
        final var aesKey = new SecretKeySpec(new byte[32], "AES");
        final var b = JweAuthenticationConfigurer.builder();
        b.matchHeader(HttpHeaders.AUTHORIZATION, "Bearer");
        b.matchToken((header, jwe) -> Match.STRICT);
        b.decrypter(new com.nimbusds.jose.crypto.AESDecrypter(aesKey));
        b.principal((header, claims) -> claims.getSubject());
        b.authorities(new RolesGroupsAndScopesFromClaims(List.of()));
        final var processor = new JwtTokenProcessor(List.of(), List.of(b.build()));

        final var claims = new JWTClaimsSet.Builder()
                .subject("bob")
                .claim("roles", List.of("USER"))
                .issueTime(Date.from(Instant.now()))
                .build();
        final var jwe = new EncryptedJWT(new JWEHeader(JWEAlgorithm.A256KW, EncryptionMethod.A128GCM), claims);
        jwe.encrypt(new AESEncrypter(aesKey));

        final PrincipalAndAuthorities result = processor.process(hs, jwe.serialize());
        Assertions.assertNotNull(result, "symmetric raw-claims JWE should be accepted without an inner signature");
        Assertions.assertEquals("bob", result.principal());
        Assertions.assertTrue(result.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    public void issuerSignedAndEncryptedTokenIsAccepted() throws Exception {
        final var claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("roles", List.of("USER"))
                .issueTime(Date.from(Instant.now()))
                .build();
        final var token = nestedJwe(issuerPrivate, claims);

        final PrincipalAndAuthorities result = processor.process(hs, token);
        Assertions.assertNotNull(result);
        Assertions.assertEquals("alice", result.principal());
        Assertions.assertTrue(result.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    public void rawClaimsJweWithoutInnerSignatureIsRejected() throws Exception {
        // attacker encrypts arbitrary claims to the recipient using only the recipient's public key
        final var claims = new JWTClaimsSet.Builder()
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .issueTime(Date.from(Instant.now()))
                .build();
        final var token = rawClaimsJwe(claims);
        Assertions.assertThrows(BadCredentialsException.class, () -> processor.process(hs, token));
    }

    @Test
    public void nestedJwsSignedWithWrongKeyIsRejected() throws Exception {
        // attacker signs the inner JWS with their own key (not the issuer's), then encrypts to recipient
        final var attackerKey = new ECKeyGenerator(Curve.P_256).generate();
        final var claims = new JWTClaimsSet.Builder()
                .subject("attacker")
                .claim("roles", List.of("ADMIN"))
                .issueTime(Date.from(Instant.now()))
                .build();
        final var token = nestedJwe(attackerKey.toECPrivateKey(), claims);
        Assertions.assertThrows(BadCredentialsException.class, () -> processor.process(hs, token));
    }
}
