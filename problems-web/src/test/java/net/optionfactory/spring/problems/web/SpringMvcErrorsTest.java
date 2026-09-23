package net.optionfactory.spring.problems.web;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(SpringMvcErrorsTest.Config.class)
public class SpringMvcErrorsTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public ErrorsController errorsController() {
            return new ErrorsController();
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            ExceptionResolvers.configurer(resolvers).rest(new JsonMapper()).configure();
        }
    }

    @RestController
    public static class ErrorsController {

        @PostMapping("/body")
        public Map<String, Object> body(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/header")
        public String header(@RequestHeader("X-Required") String value) {
            return value;
        }

        @GetMapping("/map")
        public Map<String, Object> map() {
            return Map.of("a", 1);
        }

        @GetMapping("/unmapped-variable")
        public String unmappedVariable(@PathVariable("id") String id) {
            return id;
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
    public void anUnsupportedBodyTypeIsAnsweredWithItsStatusAndTheSupportedTypes() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(MockMvcResultMatchers.status().isUnsupportedMediaType())
                .andExpect(MockMvcResultMatchers.header().exists("Accept"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].reason").value(Matchers.containsString("text/plain")));
    }

    @Test
    public void aMissingHeaderIsABadRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/header"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("BAD_REQUEST"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].reason").value(Matchers.containsString("X-Required")));
    }

    @Test
    public void anUnacceptableResponseTypeIsAnsweredWithItsStatus() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/map").accept(MediaType.valueOf("text/csv")))
                .andExpect(MockMvcResultMatchers.status().isNotAcceptable())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("NOT_ACCEPTABLE"));
    }

    @Test
    public void aServerSideMappingErrorKeepsItsServerErrorStatus() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/unmapped-variable"))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    public void aWrongMethodIsAnsweredBySpringBeforeAnyHandlerExists() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/header"))
                .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed())
                .andExpect(MockMvcResultMatchers.header().string("Allow", "GET"));
    }
}
