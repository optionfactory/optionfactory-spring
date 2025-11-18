package net.optionfactory.spring.data.jpa.web.examples;

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
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
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
import tools.jackson.databind.json.JsonMapper;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = WebConfig.class)
@WebAppConfiguration
public class DataJpaWebExampleTest {

    @Configuration
    @EnableWebMvc
    public static class WebConfig implements WebMvcConfigurer {

        @Bean
        public JsonMapper restJsonMapper() {
            return JsonMapper.builder()
                    /**
                     * Registering this mixin simplifies the Page mapping
                     * exposing only size and data.
                     */
                    .addMixIn(Page.class, PageMixin.class)
                    .build();
        }

        @Inject
        public JsonMapper restJsonMapper;
        
        

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            /**
             * the jsonMapper needs to be configured in the
             * JacksonJsonHttpMessageConverter so the Page mixin is used
             */
            converters.add(new JacksonJsonHttpMessageConverter(restJsonMapper));
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
            resolvers.add(new FilterRequestArgumentResolver(restJsonMapper));
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
