package net.optionfactory.authentication.token.examples;

import io.jsonwebtoken.Jwts;
import net.optionfactory.authentication.token.examples.TokenAuthenticationExampleTest.SecurityConfig;
import net.optionfactory.authentication.token.examples.TokenAuthenticationExampleTest.WebConfig;
import net.optionfactory.spring.authentication.token.HttpHeaderAuthentication;
import net.optionfactory.spring.authentication.token.UnauthorizedStatusEntryPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {
            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.jwe(Jwts.SIG.HS256.key().build(), Customizer.withDefaults());
                c.token("M2M_SECRET", "principal1", "ROLE_M2M");
                c.token("ANOTHER_SECRET", "principal2", "ROLE_ANOTHER");
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

}
