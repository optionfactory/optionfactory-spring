package net.optionfactory.spring.authentication.tokens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationCustomProcessorTest.SecurityConfig;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationCustomProcessorTest.WebConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/// A custom processor is registered on its own header and scheme, so the filter looks for tokens
/// there even when no other configuration does.
@SpringJUnitWebConfig({
    WebConfig.class,
    SecurityConfig.class
})
public class HttpHeaderAuthenticationCustomProcessorTest {

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {
            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.processor("X-Custom", "", (hs, token) -> "good".equals(token) ? new PrincipalAndAuthorities("custom", List.of(new SimpleGrantedAuthority("ROLE_CUSTOM"))) : null);
            });
            http.authorizeHttpRequests(c -> {
                c.anyRequest().hasRole("CUSTOM");
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

        @GetMapping("/ping")
        @ResponseBody
        public String ping() {
            return "pong";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    public void aCustomProcessorSeesTokensOnItsOwnHeader() throws Exception {
        final var response = mvc.perform(get("/ping").header("X-Custom", "good")).andReturn().getResponse();
        Assertions.assertEquals(200, response.getStatus(), "a token on the custom processor's own header is found and authenticated, with no other configuration registering that header");
    }

    @Test
    public void aRequestWithoutTheCustomHeaderIsNotAuthenticated() throws Exception {
        final var response = mvc.perform(get("/ping")).andReturn().getResponse();
        Assertions.assertEquals(403, response.getStatus(), "a request without the custom header is not authenticated by the processor");
    }
}
