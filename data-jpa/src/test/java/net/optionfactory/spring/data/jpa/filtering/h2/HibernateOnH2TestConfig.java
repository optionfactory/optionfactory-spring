package net.optionfactory.spring.data.jpa.filtering.h2;

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

@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = HibernateOnH2TestConfig.class)
@PropertySource(value = "classpath:test.properties")
public class HibernateOnH2TestConfig {

    @Bean
    public ObjectMapper hibernateMapper() {
        final var mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    @Bean
    public SessionFactory entityManagerFactory(DataSource dataSource, ObjectMapper hibernateMapper) {
        final var properties = new Properties();
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        properties.put(AvailableSettings.SHOW_SQL, false);
        properties.put(AvailableSettings.FORMAT_SQL, false);
        properties.put(AvailableSettings.USE_SQL_COMMENTS, false);
        properties.put(AvailableSettings.GENERATE_STATISTICS, false);
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
        properties.put(AvailableSettings.USE_QUERY_CACHE, false);
        properties.put(AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(hibernateMapper));
        final var builder = new LocalSessionFactoryBuilder(dataSource);
        builder.scanPackages(HibernateOnH2TestConfig.class.getPackage().getName());
        builder.setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        builder.setImplicitNamingStrategy(new ImplicitNamingStrategyComponentPathImpl());
        builder.addProperties(properties);
        return builder.buildSessionFactory();
    }

    @Bean
    public DataSource dataSource() throws PropertyVetoException {
        final var config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb");
        config.setUsername("sa");
        config.setPassword("");
        return new HikariDataSource(config);        

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
