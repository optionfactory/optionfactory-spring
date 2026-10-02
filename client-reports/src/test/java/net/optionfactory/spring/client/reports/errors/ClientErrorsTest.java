package net.optionfactory.spring.client.reports.errors;

import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.client.reports.errors.ClientErrors.ClientError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ClientErrorsTest {

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.with(ClientErrors.configurer(), c -> c.log(false));
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
            return http.build();
        }

        @Bean
        public ClientErrorsCollector collector() {
            return new ClientErrorsCollector();
        }
    }

    public static class ClientErrorsCollector {

        public final List<ClientError> errors = new ArrayList<>();

        @EventListener
        public void on(ClientError error) {
            errors.add(error);
        }
    }

    private final AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();

    @AfterEach
    public void close() {
        ac.close();
    }

    private Filter securityFilterChain() {
        ac.setServletContext(new MockServletContext());
        ac.register(SecurityConfig.class);
        ac.refresh();
        return ac.getBean("springSecurityFilterChain", Filter.class);
    }

    @Test
    public void reportsAreAcceptedAndPublishedBeforeCsrfAndAuthorization() throws Exception {
        final var chain = securityFilterChain();

        final var report = new MockHttpServletRequest("POST", "/client-errors/");
        report.setContent("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8));
        final var reportResponse = new MockHttpServletResponse();
        chain.doFilter(report, reportResponse, new MockFilterChain());

        final var other = new MockHttpServletRequest("POST", "/api/other");
        final var otherResponse = new MockHttpServletResponse();
        chain.doFilter(other, otherResponse, new MockFilterChain());

        Assertions.assertEquals(202, reportResponse.getStatus(), "an anonymous report without csrf token is accepted at the default uri");
        final var errors = ac.getBean(ClientErrorsCollector.class).errors;
        Assertions.assertEquals(1, errors.size(), "the report is published to the application context");
        Assertions.assertEquals("boom", errors.get(0).content().get("message").asString(), "the published event carries the report");
        Assertions.assertEquals(403, otherResponse.getStatus(), "the rest of the application is still protected by csrf and authorization");
    }

    @Test
    public void reportsCarryThePrincipalOfTheSession() throws Exception {
        final var chain = securityFilterChain();

        final var report = new MockHttpServletRequest("POST", "/client-errors/");
        report.setContent("{}".getBytes(StandardCharsets.UTF_8));
        final var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(new TestingAuthenticationToken("the-user", "credentials", "ROLE_USER")));
        report.setSession(session);
        chain.doFilter(report, new MockHttpServletResponse(), new MockFilterChain());

        final var errors = ac.getBean(ClientErrorsCollector.class).errors;
        Assertions.assertEquals("the-user", errors.get(0).principal(), "the security context is loaded from the session before the report is handled");
    }
}
