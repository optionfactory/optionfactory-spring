package net.optionfactory.spring.authentication.example;

import net.optionfactory.spring.authentication.CoalescingAuthentication;
import net.optionfactory.spring.authentication.Principals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(PrincipalsCoalescingTest.Config.class)
public class PrincipalsCoalescingTest {

    public record CustomPrincipal(String name) {

    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class Config {

        @Bean
        public InMemoryUserDetailsManager userDetailsService() {
            UserDetails user = User.withUsername("original_user")
                    .password("pass")
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(user);
        }

        @Bean
        public TestController testController() {
            return new TestController();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

            http.with(Principals.coalescing(CustomPrincipal.class), c -> {
                c.principal(UserDetails.class, (auth, principal) -> new CustomPrincipal(principal.getUsername()));
            });

            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .formLogin(form -> form.permitAll())
                    .build();
        }
    }

    @RestController
    public static class TestController {

        @GetMapping("/info/@me")
        public String me(@AuthenticationPrincipal CustomPrincipal principal) {
            return principal.name();
        }

        @GetMapping("/info/@auth")
        public String self(CoalescingAuthentication auth) {
            return auth.getClass().getSimpleName();
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
    @WithMockUser(username = "original_user")
    public void authenticationPrincipalIsCoalescedIntoCustomType() throws Exception {
        mvc.perform(get("/info/@me"))
                .andExpect(status().isOk())
                .andExpect(content().string("original_user"));
    }

    @Test
    @WithMockUser(username = "original_user")
    public void authenticationIsCoalescedIntoColascingAuthentication() throws Exception {
        mvc.perform(get("/info/@auth"))
                .andExpect(status().isOk())
                .andExpect(content().string("CoalescingAuthentication"));
    }

}
