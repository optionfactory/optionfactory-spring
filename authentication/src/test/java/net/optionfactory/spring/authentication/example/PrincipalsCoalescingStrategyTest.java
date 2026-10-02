package net.optionfactory.spring.authentication.example;

import net.optionfactory.spring.authentication.Principals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/// An application declaring its own `SecurityContextHolderStrategy` bean has every spring security
/// filter read and write the context through it: the coalesced authentication must be read from and
/// set there too, or the application never sees the coalesced principal.
@SpringJUnitWebConfig(PrincipalsCoalescingStrategyTest.Config.class)
public class PrincipalsCoalescingStrategyTest {

    public record CustomPrincipal(String name) {

    }

    /// A strategy holding one context, distinct from the static `SecurityContextHolder`'s.
    public static class SingleContextStrategy implements SecurityContextHolderStrategy {

        private SecurityContext context = new SecurityContextImpl();

        @Override
        public void clearContext() {
            context = new SecurityContextImpl();
        }

        @Override
        public SecurityContext getContext() {
            return context;
        }

        @Override
        public void setContext(SecurityContext context) {
            this.context = context;
        }

        @Override
        public SecurityContext createEmptyContext() {
            return new SecurityContextImpl();
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class Config {

        @Bean
        public SecurityContextHolderStrategy securityContextHolderStrategy() {
            return new SingleContextStrategy();
        }

        @Bean
        public TestController testController(SecurityContextHolderStrategy strategy) {
            return new TestController(strategy);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.with(Principals.coalescing(CustomPrincipal.class), c -> {
                c.principal("anonymousUser", new CustomPrincipal("guest"));
            });
            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @RestController
    public static class TestController {

        private final SecurityContextHolderStrategy strategy;

        public TestController(SecurityContextHolderStrategy strategy) {
            this.strategy = strategy;
        }

        @GetMapping("/info/@me")
        public String me() {
            return strategy.getContext().getAuthentication().getPrincipal().toString();
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        this.mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    public void thePrincipalIsCoalescedOnTheApplicationsStrategy() throws Exception {
        final var response = mvc.perform(get("/info/@me")).andReturn().getResponse();
        Assertions.assertEquals(200, response.getStatus(), "the anonymous request is permitted");
        Assertions.assertEquals(new CustomPrincipal("guest").toString(), response.getContentAsString(), "the anonymous principal on the application's strategy is coalesced into the guest");
    }
}
