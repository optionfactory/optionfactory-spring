package net.optionfactory.spring.data.jpa.filtering.psql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.spring.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import net.optionfactory.spring.data.jpa.test.containers.ContainerDefinition;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = HibernateOnPsqlTestConfig.class)
public class HibernateOnPsqlTestConfig {

    /**
     * Postgres shared by every test declaring {@code @SharedContainer(Postgres.class)}: started before the first
     * one, stopped after the last one; its coordinates are exposed to the environment as {@code db.*}.
     */
    public static class Postgres implements ContainerDefinition<PostgreSQLContainer> {

        @Override
        public PostgreSQLContainer start() throws Exception {
            final var image = DockerImageName.parse("optionfactory/debian13-postgres18:235").asCompatibleSubstituteFor("postgres");
            final var container = new PostgreSQLContainer(image)
                    .withExposedPorts(5432)
                    .withUsername("postgres")
                    .withDatabaseName("test");
            container.start();
            /* the image ignores POSTGRES_* env: align the server with what the container reports */
            container.execInContainer("psql", "-U", "postgres", "-c", "ALTER USER postgres PASSWORD 'test'");
            container.execInContainer("psql", "-U", "postgres", "-c", "CREATE DATABASE test");
            return container;
        }

        @Override
        public Map<String, Object> properties(PostgreSQLContainer container) {
            return Map.of(
                    "db.jdbc.url", container.getJdbcUrl(),
                    "db.username", container.getUsername(),
                    "db.password", container.getPassword()
            );
        }
    }

    @Bean
    public DataSource dataSource(
            @Value("${db.jdbc.url}") String jdbcUrl,
            @Value("${db.username}") String username,
            @Value("${db.password}") String password) {
        final var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        final var hibernateMapper = JsonMapper.builder().build();
        final var properties = new Properties();
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        properties.put(AvailableSettings.SHOW_SQL, true);
        properties.put(AvailableSettings.FORMAT_SQL, false);
        properties.put(AvailableSettings.USE_SQL_COMMENTS, false);
        properties.put(AvailableSettings.GENERATE_STATISTICS, false);
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
        properties.put(AvailableSettings.USE_QUERY_CACHE, false);
        properties.put(AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(hibernateMapper));
        properties.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, new PhysicalNamingStrategySnakeCaseImpl());
        properties.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY, new ImplicitNamingStrategyComponentPathImpl());

        final var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan(HibernateOnPsqlTestConfig.class.getPackage().getName());
        factory.setDataSource(dataSource);
        factory.setJpaProperties(properties);
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public TransactionTemplate tt(PlatformTransactionManager htt) {
        return new TransactionTemplate(htt);
    }

}
