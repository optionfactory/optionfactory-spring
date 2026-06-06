package net.optionfactory.spring.problems.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(UnifiedValidationTest.Config.class)
public class UnifiedValidationTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public MethodValidationController controller() {
            return new MethodValidationController();
        }

        @Bean
        public LocaleResolver localeResolver() {
            final var resolver = new AcceptHeaderLocaleResolver();
            resolver.setSupportedLocales(List.of(Locale.ITALIAN, Locale.FRENCH));
            resolver.setDefaultLocale(Locale.ITALIAN);
            return resolver;
        }
//
//        @Bean
//        public LocalValidatorFactoryBean validator() {
//            return new LocalValidatorFactoryBean();
//        }
//
//        @Override
//        public Validator getValidator() {
//            return validator();
//        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            List<HandlerExceptionResolver> old = new ArrayList<>(resolvers);
            resolvers.removeAll(old);
            resolvers.add(RestExceptionResolver.withDefaults(Options.INCLUDE_DETAILS, new JsonMapper()));
            resolvers.addAll(old);
        }

    }

    @RestController
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
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("letter"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("Parametro mancante")
                );
    }

    @Test
    public void invalidRequestParamIsReportedAsFieldError() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/request-param?letter=0"))
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("letter"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("deve corrispondere a \"[a-z]\"")
                );
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
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("0.value"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("non deve essere null")
                );
    }

    @Test
    public void invalidRequestBodyIsReportedAsFieldError2() throws Exception {
        final var body = """
        [null]
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("0"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("non deve essere null")
                );
    }

    @Test
    public void nullRequestBodyIsReportedAsObjectError() throws Exception {
        final var body = """
        null
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("OBJECT_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value((Object) null),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("non deve essere null")
                );
    }

    @Test
    public void nullRequestBodyIsReportedAsObjectErrorWhenRequired() throws Exception {
        final var body = """
        null
        """;

        mvc
                .perform(MockMvcRequestBuilders.post("/body-list-required").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpectAll(
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("MESSAGE_NOT_READABLE"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value((Object) null)
                );
    }
}
