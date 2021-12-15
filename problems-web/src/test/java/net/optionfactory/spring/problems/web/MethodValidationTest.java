package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import net.optionfactory.spring.problems.web.MethodValidationTest.Config;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Options;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Config.class)
@WebAppConfiguration
public class MethodValidationTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public MethodValidationController controller() {
            return new MethodValidationController();
        }

        @Bean
        public MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            List<HandlerExceptionResolver> old = new ArrayList<>(resolvers);
            resolvers.removeAll(old);
            resolvers.add(new RestExceptionResolver(new ObjectMapper(), Options.OMIT_DETAILS));
            resolvers.addAll(old);
        }

    }

    @RestController
    @Validated
    public static class MethodValidationController {

        @GetMapping("/request-param")
        public void requestParam(@Valid @Pattern(regexp = "[a-z]") @RequestParam("letter") String letter) {
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Before
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
}
