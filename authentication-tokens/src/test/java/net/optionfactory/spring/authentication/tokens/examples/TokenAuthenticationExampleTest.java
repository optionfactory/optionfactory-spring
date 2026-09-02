package net.optionfactory.spring.authentication.tokens.examples;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import net.optionfactory.spring.authentication.UnauthorizedStatusEntryPoint;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication;
import net.optionfactory.spring.authentication.tokens.examples.TokenAuthenticationExampleTest.SecurityConfig;
import net.optionfactory.spring.authentication.tokens.examples.TokenAuthenticationExampleTest.WebConfig;
import net.optionfactory.spring.authentication.tokens.jwt.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@SpringJUnitWebConfig({
    WebConfig.class,
    SecurityConfig.class
})
@WebAppConfiguration
public class TokenAuthenticationExampleTest {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN_SCHEME = "Token";

    private static final byte[] HEX_ENCODED_HS256_KEY = HexFormat.of().parseHex("7465737400000000000000000000000000000000000000000000000000000000");
    private static final byte[] SERVICE_HS256_KEY = HexFormat.of().parseHex("7365727669636500000000000000000000000000000000000000000000000000");
    private static final SecretKey JWE_AES_KEY = new SecretKeySpec(HexFormat.of().parseHex("a2f03b180c4e9d5271a6f8e4d3c2b1095867e0d4f2c1a3b5c7d9e1f30456789a"), "AES");

    private static final String VALID_HS256_JWS = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLWlzc3VlciIsImlhdCI6MTczNTY4OTYwMCwiZXhwIjo0ODkxMzYzMjAwLCJhdWQiOiJleGFtcGxlLmNvbSIsInN1YiI6InRlc3RAZXhhbXBsZS5jb20ifQ.nKzo23z0ToCMPF5FhFtaKbQSDUwBSWRslIrOdolbqJA";
    private static final String WRONG_AUDIENCE_HS256_JWS = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLWlzc3VlciIsImlhdCI6MTczNTY4OTYwMCwiZXhwIjo0ODkxMzYzMjAwLCJhdWQiOiJ3cm9uZy1hdWRpZW5jZSIsInN1YiI6InRlc3RAZXhhbXBsZS5jb20ifQ.iWgphK1jW3rvRb77dnGiINmdVZ3H2usAjSilV35mHd0";
    private static final String VALID_SERVICE_HS256_JWS = signedHs256Jws(SERVICE_HS256_KEY);
    private static final String VALID_A128GCM_JWE = encryptedA256KwJwe(JWE_AES_KEY);

    private static JWTClaimsSet.Builder exampleClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("example-issuer")
                .audience("example.com")
                .subject("test@example.com")
                .issueTime(Date.from(Instant.ofEpochSecond(1735689600)))
                .expirationTime(Date.from(Instant.ofEpochSecond(4891363200L)));
    }

    private static String signedHs256Jws(byte[] key) {
        try {
            final var jws = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), exampleClaims().build());
            jws.sign(new MACSigner(key));
            return jws.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encryptedA256KwJwe(SecretKey key) {
        try {
            final var jwe = new EncryptedJWT(new JWEHeader(JWEAlgorithm.A256KW, EncryptionMethod.A128GCM), exampleClaims().build());
            jwe.encrypt(new AESEncrypter(key));
            return jwe.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {

            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.jws(jc -> {
                    jc.matchHeader(HttpHeaders.AUTHORIZATION, "Bearer"); //this is already the default
                    jc.matchToken(Match.STRICT); //this is already the default
                    jc.verify(HEX_ENCODED_HS256_KEY);
                    jc.claims(Duration.ofSeconds(60), claims -> {
                        claims.audience("example.com");
                        claims.exact("iss", "example-issuer");
                    });
                    jc.principal("jws-principal");
                    jc.authorities("ROLE_M2M");
                });
                c.jwe(jc -> {
                    jc.matchHeader(HttpHeaders.AUTHORIZATION, "Bearer"); //this is already the default
                    jc.match(Match.STRICT); //this is already the default
                    jc.decrypt(JWE_AES_KEY);
                    jc.claims(Duration.ofSeconds(60), claims -> {
                        claims.audience("example.com");
                        claims.exact("iss", "example-issuer");
                    });
                    jc.principal("jwe-principal");
                    jc.authorities("ROLE_M2M");
                });
                c.jws(jc -> {
                    jc.matchHeader(SERVICE_TOKEN_HEADER, SERVICE_TOKEN_SCHEME);
                    jc.matchToken(Match.STRICT); //this is already the default
                    jc.verify(SERVICE_HS256_KEY);
                    jc.principal("service-principal");
                    jc.authorities("ROLE_M2M");
                });
                c.bearer("M2M_SECRET", "principal1", "ROLE_M2M");
                c.bearer("ANOTHER_SECRET", "principal2", "ROLE_ANOTHER");
                c.basic("user", "12345", "principal3", "ROLE_M2M");
            });

            http.authorizeHttpRequests(c -> {
                c.requestMatchers("/api/m2m").hasRole("M2M");
            });
            http.exceptionHandling(eh -> {
                eh.authenticationEntryPoint(UnauthorizedStatusEntryPoint.bearerChallenge());
            });
            return http.build();
        }

    }

    @Configuration
    @EnableWebMvc
    public static class WebConfig implements WebMvcConfigurer {

        @Bean
        public PingController ping() {
            return new PingController();
        }

    }

    @Controller
    public static class PingController {

        @GetMapping("/api/m2m")
        public String ping() {
            return "pong";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    public void missingTokenYields401() throws Exception {
        mvc.perform(get("/api/m2m"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    public void invalidTokenYields401() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", "Bearer UNKNOWN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validTokenAndRoleYields200() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", "Bearer M2M_SECRET"))
                .andExpect(status().isOk());
    }

    @Test
    public void validTokenWithWrongRoleYields403() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", "Bearer ANOTHER_SECRET"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void validJwsTokenYields200() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Bearer %s", VALID_HS256_JWS)))
                .andExpect(status().isOk());
    }
    
    @Test
    public void invalidClaimInJwsWithStrictModeTokenYields401() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Bearer %s", WRONG_AUDIENCE_HS256_JWS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validJweTokenOnConfiguredHeaderYields200() throws Exception {
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Bearer %s", VALID_A128GCM_JWE)))
                .andExpect(status().isOk());
    }

    @Test
    public void validJweTokenOnHeaderNotSelectedByJweProcessorYields401() throws Exception {
        //the token is decryptable and claim-valid for the Authorization: Bearer JWE processor, but it is
        //presented on X-Service-Token: Token, which only the service JWS processor selected: no processor
        //may authenticate it
        mvc.perform(get("/api/m2m").header(SERVICE_TOKEN_HEADER, String.format("%s %s", SERVICE_TOKEN_SCHEME, VALID_A128GCM_JWE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validServiceJwsOnConfiguredHeaderYields200() throws Exception {
        mvc.perform(get("/api/m2m").header(SERVICE_TOKEN_HEADER, String.format("%s %s", SERVICE_TOKEN_SCHEME, VALID_SERVICE_HS256_JWS)))
                .andExpect(status().isOk());
    }

    @Test
    public void validServiceJwsOnHeaderNotSelectedByItsProcessorYields401() throws Exception {
        //the token's signature and claims are fully valid for the service processor, but it is presented
        //on Authorization: Bearer, which the service processor did not select: only the Bearer JWS
        //processor may run, and it verifies with a different key
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Bearer %s", VALID_SERVICE_HS256_JWS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validBearerJwsOnHeaderNotSelectedByItsProcessorYields401() throws Exception {
        //symmetric to validServiceJwsOnHeaderNotSelectedByItsProcessorYields401: the Bearer JWS is fully
        //valid for its processor, but on X-Service-Token: Token only the service processor may run,
        //and it verifies with a different key
        mvc.perform(get("/api/m2m").header(SERVICE_TOKEN_HEADER, String.format("%s %s", SERVICE_TOKEN_SCHEME, VALID_HS256_JWS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validBasicAuthYields200() throws Exception {
        var basicCreds = Base64.getEncoder().encodeToString("user:12345".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Basic %s", basicCreds)))
                .andExpect(status().isOk());
    }
}
