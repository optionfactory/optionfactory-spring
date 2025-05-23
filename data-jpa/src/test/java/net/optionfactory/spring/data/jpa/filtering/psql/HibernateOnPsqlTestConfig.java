package net.optionfactory.spring.data.jpa.filtering.psql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.beans.PropertyVetoException;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.spring.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = HibernateOnPsqlTestConfig.class)
@PropertySource(value = "classpath:test.properties")
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
    public ObjectMapper hibernateMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public SessionFactory entityManagerFactory(ObjectMapper hibernateMapper, DataSource dataSource) {
        final var properties = new Properties();
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        properties.put(AvailableSettings.SHOW_SQL, true);
        properties.put(AvailableSettings.FORMAT_SQL, false);
        properties.put(AvailableSettings.USE_SQL_COMMENTS, false);
        properties.put(AvailableSettings.GENERATE_STATISTICS, false);
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
        properties.put(AvailableSettings.USE_QUERY_CACHE, false);
        properties.put(AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(hibernateMapper));
        final var builder = new LocalSessionFactoryBuilder(dataSource);
        builder.scanPackages(HibernateOnPsqlTestConfig.class.getPackage().getName());
        builder.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        builder.setImplicitNamingStrategy(new ImplicitNamingStrategyComponentPathImpl());
        builder.addProperties(properties);
        return builder.buildSessionFactory();
    }

    @Bean
    public PlatformTransactionManager transactionManager(SessionFactory hibernate) {
        return new HibernateTransactionManager(hibernate);
    }

    @Bean
    public TransactionTemplate tt(PlatformTransactionManager htt) {
        return new TransactionTemplate(htt);
    }

}
