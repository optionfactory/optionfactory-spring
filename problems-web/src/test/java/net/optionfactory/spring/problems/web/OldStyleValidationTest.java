package net.optionfactory.spring.problems.web;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(OldStyleValidationTest.Config.class)
public class OldStyleValidationTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }

        @Bean
        public Payments payments() {
            return new Payments();
        }

        @Bean
        public ValidatedController validatedController(Payments payments) {
            return new ValidatedController(payments);
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            ExceptionResolvers.configurer(resolvers).rest(new JsonMapper()).configure();
        }
    }

    public static class Payload {

        @NotNull
        public String name;
    }

    @Validated
    public static class Payments {

        public void charge(@Min(1) int amount) {
        }
    }

    @RestController
    @Validated
    public static class ValidatedController {

        private final Payments payments;

        public ValidatedController(Payments payments) {
            this.payments = payments;
        }

        @GetMapping("/parameter")
        public String parameter(@RequestParam("q") @Min(1) int query) {
            return "" + query;
        }

        @PostMapping("/body")
        public String body(@Valid @RequestBody Payload payload) {
            return payload.name;
        }

        @GetMapping("/service")
        public String service(@RequestParam("q") int query) {
            payments.charge(query);
            return "" + query;
        }
    }

    @Inject
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    public void aHandlerParameterIsReportedUnderTheNameTheClientSent() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/parameter").queryParam("q", "0"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("q"));
    }

    @Test
    public void aRequestBodyFieldIsReportedByItsPathInTheBody() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("name"));
    }

    @Test
    public void aViolationInAServiceTheHandlerCallsKeepsItsJavaPath() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/service").queryParam("q", "0"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("amount"));
    }
}
