package net.optionfactory.spring.csp.examples;

import net.optionfactory.spring.csp.StrictContentSecurityPolicy;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyHeaderWriter.ContentSecurityPolicyMode;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import net.optionfactory.spring.csp.examples.CspExampleTest.SecurityConfig;
import net.optionfactory.spring.csp.examples.CspExampleTest.WebConfig;
import org.junit.Assert;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {
    WebConfig.class,
    SecurityConfig.class
})
@WebAppConfiguration
public class CspExampleTest {

    @Configuration
    @EnableWebSecurity
    public static class SecurityConfig {

        @Bean
        public SecurityFilterChain security(HttpSecurity http) throws Exception {

            http.headers(c -> c.xssProtection(Customizer.withDefaults()));

            http.with(StrictContentSecurityPolicy.configurer(), c -> {
                c.mode(ContentSecurityPolicyMode.REPORT);
                c.reportUri("/test-csp-report/");
            });

            return http.build();
        }

    }

    @Configuration
    @EnableWebMvc
    public static class WebConfig implements WebMvcConfigurer {

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(StrictContentSecurityPolicy.addNonceToModel());
        }

        @Bean
        public PingController ping() {
            return new PingController();
        }

    }

    @Controller
    public static class PingController {

        @GetMapping("/ping")
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
    public void nonceIsInjectedInModel() throws Exception {
        final var result = mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andReturn();
        final var mav = result.getModelAndView();
        final var csp = mav.getModel().get("csp");
        Assert.assertTrue(csp instanceof Csp);
    }

    @Test
    public void responseContainsCspHeaders() throws Exception {
        final var result = mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andReturn();

        final var cspHeader = result.getResponse().getHeader("Content-Security-Policy-Report-Only");

        Assert.assertNotNull(cspHeader);
        Assert.assertTrue(cspHeader.contains("/test-csp-report/"));
        Assert.assertNotNull(result.getResponse().getHeader("X-XSS-Protection"));
    }

    @Test
    public void reportIsProcessedByFilter() throws Exception {
        mvc.perform(post("/test-csp-report/").content("""
        {
            "age": 53531,
            "body": {
              "blockedURL": "inline",
              "columnNumber": 39,
              "disposition": "enforce",
              "documentURL": "https://example.com/csp-report",
              "effectiveDirective": "script-src-elem",
              "lineNumber": 121,
              "originalPolicy": "default-src 'self'; report-to csp-endpoint-name",
              "referrer": "https://www.google.com/",
              "sample": "console.log",
              "sourceFile": "https://example.com/csp-report",
              "statusCode": 200
            },
            "type": "csp-violation",
            "url": "https://example.com/csp-report",
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
        }                                                      
        """)).andExpect(status().isAccepted());

    }
}
