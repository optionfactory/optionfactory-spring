package net.optionfactory.spring.authentication.tokens.examples;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import net.optionfactory.spring.authentication.tokens.examples.TokenAuthenticationExampleTest.SecurityConfig;
import net.optionfactory.spring.authentication.tokens.examples.TokenAuthenticationExampleTest.WebConfig;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication;
import net.optionfactory.spring.authentication.UnauthorizedStatusEntryPoint;
import net.optionfactory.spring.authentication.tokens.jwt.Match;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {
    WebConfig.class,
    SecurityConfig.class
})
@WebAppConfiguration
public class TokenAuthenticationExampleTest {

    private static final byte[] HEX_ENCODED_HS256_KEY = HexFormat.of().parseHex("7465737400000000000000000000000000000000000000000000000000000000");

    private static final String VALID_HS256_JWS = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLWlzc3VlciIsImlhdCI6MTczNTY4OTYwMCwiZXhwIjo0ODkxMzYzMjAwLCJhdWQiOiJleGFtcGxlLmNvbSIsInN1YiI6InRlc3RAZXhhbXBsZS5jb20ifQ.nKzo23z0ToCMPF5FhFtaKbQSDUwBSWRslIrOdolbqJA";
    private static final String WRONG_AUDIENCE_HS256_JWS = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJleGFtcGxlLWlzc3VlciIsImlhdCI6MTczNTY4OTYwMCwiZXhwIjo0ODkxMzYzMjAwLCJhdWQiOiJ3cm9uZy1hdWRpZW5jZSIsInN1YiI6InRlc3RAZXhhbXBsZS5jb20ifQ.iWgphK1jW3rvRb77dnGiINmdVZ3H2usAjSilV35mHd0";

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {

            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.jws(jc -> {
                    jc.match(Match.STRICT);
                    jc.verify(HEX_ENCODED_HS256_KEY);
                    jc.claims(Duration.ofSeconds(60), claims -> {
                        claims.audience("example.com");
                        claims.exact("iss", "example-issuer");
                    });
                    jc.principal("jws-principal");
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

    @Before
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

    // TODO: ensure jws / jwe check tokenSelector

    @Test
    public void validBasicAuthYields200() throws Exception {
        var basicCreds = Base64.getEncoder().encodeToString("user:12345".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/m2m").header("Authorization", String.format("Basic %s", basicCreds)))
                .andExpect(status().isOk());
    }
}
