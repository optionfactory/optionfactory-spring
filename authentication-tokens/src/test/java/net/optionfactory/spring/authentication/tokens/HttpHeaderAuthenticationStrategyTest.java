package net.optionfactory.spring.authentication.tokens;

import net.optionfactory.spring.authentication.UnauthorizedStatusEntryPoint;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationFilterTest.SingleContextStrategy;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationStrategyTest.SecurityConfig;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationStrategyTest.WebConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/// An application declaring its own `SecurityContextHolderStrategy` bean has every spring security
/// filter read and write the context through it: the token's authentication must be set there too,
/// or the authorization rules never see it.
@SpringJUnitWebConfig({
    WebConfig.class,
    SecurityConfig.class
})
public class HttpHeaderAuthenticationStrategyTest {

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityContextHolderStrategy securityContextHolderStrategy() {
            return new SingleContextStrategy();
        }

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {
            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.bearer("M2M_SECRET", "m2m", "ROLE_M2M");
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
    public static class WebConfig {

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
    public void anAcceptedTokenIsSeenByTheAuthorizationRules() throws Exception {
        final var response = mvc.perform(get("/api/m2m").header("Authorization", "Bearer M2M_SECRET")).andReturn().getResponse();
        Assertions.assertEquals(200, response.getStatus(), "the token's authentication is set on the application's strategy, where the authorization rules read it");
    }

    @Test
    public void aMissingTokenIsStillUnauthorized() throws Exception {
        final var response = mvc.perform(get("/api/m2m")).andReturn().getResponse();
        Assertions.assertEquals(401, response.getStatus(), "a request without a token is not authenticated");
    }
}
