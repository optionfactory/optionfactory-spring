package net.optionfactory.spring.spring.data.jpa;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.spring.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import net.optionfactory.spring.data.jpa.filtering.TestMarker;
import net.optionfactory.spring.data.jpa.hibernate.naming.LowercaseUnderscoreSeparatedPhysicalNamingStrategy;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.dialect.PostgreSQL10Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@Configuration
@EnableJpaWhitelistFilteringRepositories(
        basePackageClasses = TestMarker.class, 
        entityManagerFactoryRef = "hibernate"
)
@PropertySource(value = "classpath:test.properties")
public class HibernateTestConfig {

    @Bean
    public SessionFactory hibernate(DataSource dataSource) {
        final Properties hibernateProperties = new Properties();
        hibernateProperties.put("hibernate.dialect", PostgreSQL10Dialect.class.getName());
        hibernateProperties.put("hibernate.hbm2ddl.auto", "update");
        hibernateProperties.put("hibernate.show_sql", false);
        hibernateProperties.put("hibernate.format_sql", false);
        hibernateProperties.put("hibernate.generate_statistics", false);
        final LocalSessionFactoryBuilder builder = new LocalSessionFactoryBuilder(dataSource);
        builder.addProperties(hibernateProperties);
        builder.scanPackages(TestMarker.class.getPackage().getName());
        builder.setPhysicalNamingStrategy(new LowercaseUnderscoreSeparatedPhysicalNamingStrategy());
        builder.setImplicitNamingStrategy(new ImplicitNamingStrategyComponentPathImpl());
        return builder.buildSessionFactory();
    }

    @Bean(initMethod = "start")
    public PostgreSQLContainer postgres(
            @Value("${db.schema}") String schema,
            @Value("${db.username}") String username,
            @Value("${db.password}") String password
    ) {
        return new PostgreSQLContainer("postgres:10.8-alpine")
                .withDatabaseName(schema)
                .withUsername(username)
                .withPassword(password);
    }

    @Bean
    public DataSource dataSource(PostgreSQLContainer postgres) throws PropertyVetoException {
        final Properties driverProperties = new Properties();
        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setProperties(driverProperties);
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClass(org.postgresql.Driver.class.getName());
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setInitialPoolSize(5);
        dataSource.setMaxPoolSize(50);
        dataSource.setMinPoolSize(5);
        dataSource.setAcquireIncrement(1);
        dataSource.setAcquireRetryAttempts(3);
        dataSource.setMaxIdleTime(60);
        dataSource.setPreferredTestQuery("select 1");
        dataSource.setTestConnectionOnCheckout(true);
        return dataSource;
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
