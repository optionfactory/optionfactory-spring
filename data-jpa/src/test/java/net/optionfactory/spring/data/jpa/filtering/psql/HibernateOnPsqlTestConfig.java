package net.optionfactory.spring.data.jpa.filtering.psql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.beans.PropertyVetoException;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.spring.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import net.optionfactory.spring.data.jpa.Jackson3JsonFormatMapper;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = HibernateOnPsqlTestConfig.class)
public class HibernateOnPsqlTestConfig {

    @Bean
    public JdbcDatabaseContainer<?> dbContainer() {
        final var container = new PostgreSQLContainer("postgres:17")
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
        container.start();
        return container;
    }

    @Bean
    public DataSource dataSource(JdbcDatabaseContainer<?> dbContainer) throws PropertyVetoException {
        final var config = new HikariConfig();
        config.setJdbcUrl(dbContainer.getJdbcUrl());
        config.setUsername(dbContainer.getUsername());
        config.setPassword(dbContainer.getPassword());
        return new HikariDataSource(config);
    }

    @Bean
    public JsonMapper hibernateMapper() {
        return new JsonMapper();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, JsonMapper hibernateMapper) {
        final var properties = new Properties();
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        properties.put(AvailableSettings.SHOW_SQL, false);
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
