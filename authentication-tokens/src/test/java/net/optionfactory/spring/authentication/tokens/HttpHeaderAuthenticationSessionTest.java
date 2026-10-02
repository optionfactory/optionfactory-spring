package net.optionfactory.spring.authentication.tokens;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationSessionTest.SecurityConfig;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthenticationSessionTest.WebConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/// A token authenticates the request it arrives with, never the session that request belongs to:
/// the security context stored in the `HttpSession` is the very instance spring security hands to
/// the filters, so writing the token's authentication into it would make the token's identity
/// stick to the session.
@SpringJUnitWebConfig({
    WebConfig.class,
    SecurityConfig.class
})
public class HttpHeaderAuthenticationSessionTest {

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {
            http.with(HttpHeaderAuthentication.configurer(), c -> {
                c.bearer("M2M_SECRET", "m2m", "ROLE_M2M");
            });
            http.authorizeHttpRequests(c -> {
                c.requestMatchers("/api/m2m").hasRole("M2M");
                c.anyRequest().authenticated();
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
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static MockHttpSession sessionOf(String username, String role) {
        final var session = new MockHttpSession();
        final SecurityContext stored = new SecurityContextImpl(new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, stored);
        return session;
    }

    @Test
    public void aTokenSentWithASessionAuthenticatesThatRequestOnly() throws Exception {
        final var session = sessionOf("alice", "ROLE_USER");

        final var withToken = mvc.perform(get("/api/m2m").session(session).header("Authorization", "Bearer M2M_SECRET")).andReturn().getResponse();
        Assertions.assertEquals(200, withToken.getStatus(), "the request carrying the token is authenticated as the token's principal");

        final var stored = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Assertions.assertEquals("alice", stored.getAuthentication().getName(), "the session keeps its own identity after a request carrying a token");

        final var withoutToken = mvc.perform(get("/api/m2m").session(session)).andReturn().getResponse();
        Assertions.assertEquals(403, withoutToken.getStatus(), "a later request on the same session, without the token, does not get the token's roles");
    }
}
