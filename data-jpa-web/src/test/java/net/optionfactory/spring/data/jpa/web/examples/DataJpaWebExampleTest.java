package net.optionfactory.spring.data.jpa.web.examples;

import jakarta.inject.Inject;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.web.PageMixin;
import net.optionfactory.spring.data.jpa.web.examples.DataJpaWebExampleTest.WebConfig;
import net.optionfactory.spring.data.jpa.web.filtering.FilterRequestArgumentResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
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

@SpringJUnitWebConfig(WebConfig.class)
public class DataJpaWebExampleTest {

    /// The setup an application needs: the [PageMixin] registered on the mapper, so that a `Page`
    /// is written as its total `size` and its `data`; that same mapper handed to the
    /// `JacksonJsonHttpMessageConverter`, or the mixin would not apply to responses; and the
    /// [FilterRequestArgumentResolver] mapping the `filters` query parameter to a `FilterRequest`.
    @Configuration
    @EnableWebMvc
    public static class WebConfig implements WebMvcConfigurer {

        @Bean
        public JsonMapper restJsonMapper() {
            return JsonMapper.builder()
                    .addMixIn(Page.class, PageMixin.class)
                    .build();
        }

        @Inject
        public JsonMapper restJsonMapper;

        @Override
        public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(restJsonMapper));
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            final var pageableResolver = new PageableHandlerMethodArgumentResolver();
            pageableResolver.setFallbackPageable(PageRequest.of(0, 100));
            pageableResolver.setMaxPageSize(Integer.MAX_VALUE);
            resolvers.add(pageableResolver);
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
            Assertions.assertEquals(2, pr.getPageNumber(), "the page parameter is mapped to the pageable");
            Assertions.assertEquals(345, pr.getPageSize(), "the size parameter is mapped to the pageable");
            Assertions.assertTrue(fr.filters().containsKey("byPetType"), "expected byPetType to be mapped");
            Assertions.assertTrue(fr.filters().containsKey("byPetBirthDate"), "expected byPetBirthDate to be mapped");
            Assertions.assertTrue(fr.filters().containsKey("byPetName"), "expected byPetName to be mapped");
            return Page.empty();
        }

    }

    @Inject
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
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
