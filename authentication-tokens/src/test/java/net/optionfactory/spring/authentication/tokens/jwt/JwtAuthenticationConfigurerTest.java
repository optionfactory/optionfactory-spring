package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthenticationConfigurerTest {

    private static final byte[] KEY = "a-shared-key-of-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_KEY = "an-aes-key-of-exactly-32-bytes!!".getBytes(StandardCharsets.UTF_8);
    private static ECKey recipient;

    @BeforeAll
    public static void generateKeys() throws Exception {
        recipient = new ECKeyGenerator(Curve.P_256).generate();
    }

    private static String hs256(JWTClaimsSet claims) throws Exception {
        final var jws = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jws.sign(new MACSigner(KEY));
        return jws.serialize();
    }

    private static Set<String> names(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    public void aJwsConfigurationReadsTheBearerAuthorizationHeaderByDefault() {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.verify(KEY);
        b.principal("svc");

        Assertions.assertEquals(new HeaderAndScheme("Authorization", "Bearer"), b.build().hs(), "without matchHeader the token is looked for on Authorization: Bearer");
    }

    @Test
    public void aJwsConfigurationRequiresAVerifierAndAPrincipal() {
        final var withoutVerifier = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        withoutVerifier.principal("svc");
        Assertions.assertThrows(IllegalArgumentException.class, withoutVerifier::build, "a jws configuration cannot be built without a verifier");

        final var withoutPrincipal = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        withoutPrincipal.verify(KEY);
        Assertions.assertThrows(IllegalArgumentException.class, withoutPrincipal::build, "a jws configuration cannot be built without a principal");
    }

    @Test
    public void aConfigurationRequiresAClaimsPolicy() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> JwsAuthenticationConfigurer.builder(null), "the claims policy must always be stated");
        Assertions.assertThrows(IllegalArgumentException.class, () -> JweAuthenticationConfigurer.builder(null), "the claims policy must always be stated");
    }

    @Test
    public void anHmacSecretShorterThan256BitsIsRefused() {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());

        Assertions.assertThrows(IllegalStateException.class, () -> b.verify(new byte[16]), "a 128 bit hmac secret is too short to be configured");
    }

    @Test
    public void anAsymmetricJweRequiresAnInnerVerifier() throws Exception {
        final var b = JweAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.decrypter(new ECDHDecrypter(recipient.toECPrivateKey()));
        b.principal("svc");

        Assertions.assertThrows(IllegalArgumentException.class, b::build, "anyone can encrypt to a public key, so an asymmetric jwe must authenticate its issuer with an inner signature");
    }

    @Test
    public void aSymmetricJweNeedsNoInnerVerifier() {
        final var b = JweAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.decrypt(AES_KEY);
        b.principal("svc");

        Assertions.assertNull(b.build().innerVerifier(), "with a shared key the key holders are trusted as issuers, and raw claims are read");
    }

    @Test
    public void claimsDeriveTheAuthoritiesByDefault() throws Exception {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.verify(KEY);
        b.principal("svc");
        final var processor = new JwtTokenProcessor(List.of(b.build()), List.of());

        final var result = processor.process(new HeaderAndScheme("Authorization", "Bearer"), hs256(new JWTClaimsSet.Builder().claim("roles", List.of("admin")).build()));

        Assertions.assertEquals(Set.of("ROLE_ADMIN"), names(result.authorities()), "without an authorities configuration the roles, groups and scope claims decide");
    }

    @Test
    public void configuredAuthoritiesReplaceTheClaimDerivedOnes() throws Exception {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.verify(KEY);
        b.principal("svc");
        b.authorities("ROLE_M2M");
        final var processor = new JwtTokenProcessor(List.of(b.build()), List.of());

        final var result = processor.process(new HeaderAndScheme("Authorization", "Bearer"), hs256(new JWTClaimsSet.Builder().claim("roles", List.of("admin")).build()));

        Assertions.assertEquals(Set.of("ROLE_M2M"), names(result.authorities()), "static authorities are granted instead of, not besides, those in the claims");
    }

    @Test
    public void matchHeaderWithoutSchemeReadsTheBareToken() {
        final var b = JwsAuthenticationConfigurer.builder(ClaimsPolicy.permissive());
        b.verify(KEY);
        b.principal("svc");
        b.matchHeaderWithoutScheme("X-Jwt");

        Assertions.assertEquals(HeaderAndScheme.schemeless("X-Jwt"), b.build().hs(), "the token is the header's whole value");
    }
}
