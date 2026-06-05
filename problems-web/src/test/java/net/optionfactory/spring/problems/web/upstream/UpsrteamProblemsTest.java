package net.optionfactory.spring.problems.web.upstream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.problems.web.RestExceptionResolver;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Options;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(UpsrteamProblemsTest.Config.class)
public class UpsrteamProblemsTest {

    @Configuration
    @EnableWebMvc
    public static class Config implements WebMvcConfigurer {

        @Bean
        public UpstreamMappedController controller() {
            return new UpstreamMappedController();
        }

        @Bean
        public static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            List<HandlerExceptionResolver> old = new ArrayList<>(resolvers);
            resolvers.removeAll(old);
            resolvers.add(RestExceptionResolver.withDefaults(Options.INCLUDE_DETAILS, new JsonMapper()));
            resolvers.addAll(old);
        }

    }

    @RestController
    public static class UpstreamMappedController {

        @GetMapping("/without-error-mapping")
        public void withoutErrorMapping() {
            final var converters = new MessageConverters(HttpMessageConverters.forClient().registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter()).build());

            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/failures+json"));

            throw new RestClientUpstreamException(converters, "upstream", "endpoint", "reason", HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), headers,
                    """
                    [{
                        "type": "FIELD_ERROR",                           
                        "context": "field",
                        "reason": "must not be null"
                    }]
                    """.getBytes(StandardCharsets.UTF_8));
        }

        @GetMapping("/with-forward")
        @UpstreamProblems.Forward(target=HttpStatus.BAD_REQUEST)
        public void withForward() {
            final var converters = new MessageConverters(HttpMessageConverters.forClient().registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter()).build());
            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/failures+json"));
            throw new RestClientUpstreamException(converters, "upstream", "endpoint", "reason", HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), headers,
                    """
                    [{
                        "type": "FIELD_ERROR",                           
                        "context": "field",
                        "reason": "must not be null"
                    }]
                    """.getBytes(StandardCharsets.UTF_8));
        }

        @GetMapping("/with-map-context")
        @UpstreamProblems.Forward(target=HttpStatus.BAD_REQUEST)
        @UpstreamProblems.MapContext(source = "request.")
        public void withMapContext() {
            final var converters = new MessageConverters(HttpMessageConverters.forClient().registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter()).build());
            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/failures+json"));
            throw new RestClientUpstreamException(converters, "upstream", "endpoint", "reason", HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), headers,
                    """
                    [{
                        "type": "FIELD_ERROR",                           
                        "context": "request.field",
                        "reason": "must not be null"
                    }]
                    """.getBytes(StandardCharsets.UTF_8));
        }

    }
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    public void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    public void withoutErrorMappingIsMappedToBadGateway() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/without-error-mapping").contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        MockMvcResultMatchers.status().isBadGateway(),
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("UPSTREAM_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value((Object) null),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("upstream failure")
                );
    }

    @Test
    public void withoutForwardIsMapped() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/with-forward").contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        MockMvcResultMatchers.status().isBadRequest(),
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("field"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("must not be null")
                );
    }

    @Test
    public void withMapContextIsMapped() throws Exception {
        mvc
                .perform(MockMvcRequestBuilders.get("/with-map-context").contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        MockMvcResultMatchers.status().isBadRequest(),
                        MockMvcResultMatchers.jsonPath("$.[0].type").value("FIELD_ERROR"),
                        MockMvcResultMatchers.jsonPath("$.[0].context").value("field"),
                        MockMvcResultMatchers.jsonPath("$.[0].reason").value("must not be null")
                );
    }
}
