package net.optionfactory.spring.problems.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@SpringJUnitWebConfig(MethodValidationTest.Config.class)
public class MethodValidationTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public MethodValidationController controller() {
            return new MethodValidationController();
        }

        @Bean
        public static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            List<HandlerExceptionResolver> old = new ArrayList<>(resolvers);
            resolvers.removeAll(old);
            resolvers.add(new RestExceptionResolver(new JsonMapper(), Options.OMIT_DETAILS));
            resolvers.addAll(old);
        }

    }

    @RestController
    @Validated
    public static class MethodValidationController {

        @GetMapping("/request-param")
        public void requestParam(@Valid @Pattern(regexp = "[a-z]") @RequestParam("letter") String letter) {
        }

        @PostMapping("/body-list")
        public void bodyList(@Valid @RequestBody(required = false) @NotNull List<@Valid @NotNull Dto> request) {
        }

        @PostMapping("/body-list-required")
        public void bodyListRequired(@Valid @RequestBody @NotNull List<@Valid @NotNull Dto> request) {
        }
    }

    public record Dto(@NotNull String value) {

    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    public void missingServletRequestParameterExceptionIsMappedToFieldError() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/request-param"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value("letter"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].reason").value("Parameter is missing"));
    }

    @Test
    public void invalidRequestParamIsReportedAsFieldError() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/request-param?letter=0"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value("letter"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].reason").value("must match \"[a-z]\""));
    }

    @Test
    public void invalidRequestBodyIsReportedAsFieldError() throws Exception {
        final var body = """
        [{
            "value": null
        }]
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value("0.value"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].reason").value("must not be null"));
    }

    @Test
    public void invalidRequestBodyIsReportedAsFieldError2() throws Exception {
        final var body = """
        [null]
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value("0"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].reason").value("must not be null"));
    }

    @Test
    public void nullRequestBodyIsReportedAsObjectError() throws Exception {
        final var body = """
        null
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("OBJECT_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value((Object) null))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].reason").value("must not be null"));
    }

    @Test
    public void nullRequestBodyIsReportedAsObjectErrorWhenRequired() throws Exception {
        final var body = """
        null
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list-required").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].type").value("MESSAGE_NOT_READABLE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.[0].context").value((Object) null));
    }
}
