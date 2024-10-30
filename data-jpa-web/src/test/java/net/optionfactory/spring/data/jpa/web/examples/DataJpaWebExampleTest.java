package net.optionfactory.spring.data.jpa.web.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.web.PageMixin;
import net.optionfactory.spring.data.jpa.web.examples.DataJpaWebExampleTest.WebConfig;
import net.optionfactory.spring.data.jpa.web.filtering.FilterRequestArgumentResolver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = WebConfig.class)
@WebAppConfiguration
public class DataJpaWebExampleTest {

    @Configuration
    @EnableWebMvc
    public static class WebConfig implements WebMvcConfigurer {

        @Bean
        public ObjectMapper restObjectMapper() {
            return new Jackson2ObjectMapperBuilder()
                    /**
                     * Registering this mixin simplifies the Page mapping
                     * exposing only size and data.
                     */
                    .mixIn(Page.class, PageMixin.class)
                    .build();
        }

        @Inject
        public ObjectMapper restObjectMapper;

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            /**
             * the ObjectMapper needs to be configured in the
             * MappingJackson2HttpMessageConverter so the Page mixin is used
             */
            converters.add(new MappingJackson2HttpMessageConverter(restObjectMapper));
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            final var pageableResolver = new PageableHandlerMethodArgumentResolver();
            pageableResolver.setFallbackPageable(PageRequest.of(0, 100));
            pageableResolver.setMaxPageSize(Integer.MAX_VALUE);
            resolvers.add(pageableResolver);
            /**
             * this resolver handles mapping FilterRequests from query
             * parameters
             */
            resolvers.add(new FilterRequestArgumentResolver(restObjectMapper));
        }

        @Bean
        public ExampleController c() {
            return new ExampleController();
        }
    }

    @RestController
    public static class ExampleController {

        @GetMapping("/items")
        public Page<Object> search(Pageable pr, FilterRequest fr) {
            Assert.assertEquals(2, pr.getPageNumber());
            Assert.assertEquals(345, pr.getPageSize());
            Assert.assertTrue("expected byPetType to be mapped", fr.filters().containsKey("byPetType"));
            Assert.assertTrue("expected byPetBirthDate to be mapped", fr.filters().containsKey("byPetType"));
            Assert.assertTrue("expected byPetName to be mapper", fr.filters().containsKey("byPetType"));
            return Page.empty();
        }

    }

    @Inject
    private WebApplicationContext context;

    private MockMvc mvc;

    @Before
    public void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();
    }

    @Test
    public void pageablesFilterRequestsAndPagesAreMapped() throws Exception {
        final var req = MockMvcRequestBuilders
                .get("/items")
                .queryParam("page", "2")
                .queryParam("size", "345")
                .queryParam("filters", """
                    {"byPetType":["DOG"],"byPetBirthDate":["GT","1800-03-01"],"byPetName":["CONTAINS","IGNORE_CASE","O"]}        
                """)
                .accept(MediaType.APPLICATION_JSON);

        mvc.perform(req)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray())
                .andReturn();

    }

}
