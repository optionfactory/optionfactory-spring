package net.optionfactory.spring.problems.web.datajpa;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.spring.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.web.filtering.FilterRequestArgumentResolver;
import net.optionfactory.spring.problems.web.datajpa.FilteringExceptionClassifierTest.WebConfig;
import net.optionfactory.spring.problems.web.ExceptionResolvers;
import org.hamcrest.Matchers;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(WebConfig.class)
public class FilteringExceptionClassifierTest {

    @Entity
    @TextCompare(name = "byName", path = "name")
    @LocalDateCompare(name = "byBirthDate", path = "birthDate")
    @Sortable(name = "byName", path = "name")
    public static class Pet {

        @Id
        public long id;
        public String name;
        public LocalDate birthDate;
    }

    public interface PetsRepository extends JpaRepository<Pet, Long>, WhitelistFilteringRepository<Pet> {

    }

    @RestController
    public static class PetsController {

        @Inject
        private PetsRepository pets;
        @Inject
        private TransactionTemplate tx;

        @GetMapping("/pets")
        public List<Pet> search(FilterRequest fr, Sort sort) {
            return tx.execute(status -> pets.findAll(fr, sort));
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableJpaWhitelistFilteringRepositories(basePackageClasses = FilteringExceptionClassifierTest.class)
    public static class WebConfig implements WebMvcConfigurer {

        private final JsonMapper jsonMapper = JsonMapper.builder().build();

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new SortHandlerMethodArgumentResolver());
            resolvers.add(new FilterRequestArgumentResolver(jsonMapper));
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            ExceptionResolvers.configurer(resolvers)
                    .rest(jsonMapper, rest -> rest.withModule(new DataJpaProblemsModule()))
                    .configure();
        }

        @Bean
        public PetsController petsController() {
            return new PetsController();
        }

        @Bean
        public DataSource dataSource() {
            final var config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:filtering-exception-classifier");
            config.setUsername("sa");
            config.setPassword("");
            return new HikariDataSource(config);
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            final var properties = new Properties();
            properties.put(AvailableSettings.HBM2DDL_AUTO, "create-drop");
            final var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan(FilteringExceptionClassifierTest.class.getPackage().getName());
            factory.setDataSource(dataSource);
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
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
    public void aWellFormedFilterIsAnsweredNormally() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/pets")
                .queryParam("filters", """
                    {"byName":["EQ","CASE_SENSITIVE","rex"]}
                """)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    public void aMalformedFilterValueIsABadRequest() throws Exception {
        final var result = mvc.perform(MockMvcRequestBuilders.get("/pets")
                .queryParam("filters", """
                    {"byBirthDate":["EQ","not-a-date"]}
                """)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("byBirthDate"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].reason").value(Matchers.startsWith("cannot parse 'not-a-date' as a local date")))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].details").value(Matchers.nullValue()))
                .andReturn();

        final var resolved = result.getResolvedException();
        Assertions.assertInstanceOf(InvalidDataAccessApiUsageException.class, resolved);
        Assertions.assertInstanceOf(InvalidFilterRequest.class, resolved.getCause());
    }

    @Test
    public void anUnknownFilterIsABadRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/pets")
                .queryParam("filters", """
                    {"byOwner":["EQ","CASE_SENSITIVE","bob"]}
                """)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("byOwner"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].reason").value(Matchers.not(Matchers.containsString("Pet"))));
    }

    @Test
    public void anUnknownSorterIsABadRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/pets")
                .queryParam("sort", "byAge,asc")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("FIELD_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].context").value("byAge"));
    }

    @Test
    public void anUnrelatedExceptionIsDeclined() {
        Assertions.assertNull(new FilteringExceptionClassifier().classify(null, new IllegalStateException("a bug")));
    }

    @Test
    public void aRejectionNotWrappedIsMappedToo() {
        final var mapped = new FilteringExceptionClassifier().classify(null, new InvalidFilterRequest("byName", null, "operator LIKE not whitelisted"));
        Assertions.assertEquals(400, mapped.status().value());
        Assertions.assertEquals("byName", mapped.problems().get(0).context);
    }
}
