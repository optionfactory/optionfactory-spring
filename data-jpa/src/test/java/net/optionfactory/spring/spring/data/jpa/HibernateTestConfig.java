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
import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableJpaWhitelistFilteringRepositories(
        basePackageClasses = TestMarker.class,
        entityManagerFactoryRef = "entityManagerFactory"
)
@PropertySource(value = "classpath:test.properties")
public class HibernateTestConfig {

    @Bean
    public SessionFactory entityManagerFactory(DataSource dataSource) {
        final var properties = new Properties();
        properties.put(AvailableSettings.HBM2DDL_AUTO, "update");
        properties.put(AvailableSettings.SHOW_SQL, false);
        properties.put(AvailableSettings.FORMAT_SQL, false);
        properties.put(AvailableSettings.USE_SQL_COMMENTS, false);
        properties.put(AvailableSettings.GENERATE_STATISTICS, false);
        properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
        properties.put(AvailableSettings.USE_QUERY_CACHE, false);
        final var builder = new LocalSessionFactoryBuilder(dataSource);
        builder.scanPackages(TestMarker.class.getPackage().getName());
        builder.setPhysicalNamingStrategy(new LowercaseUnderscoreSeparatedPhysicalNamingStrategy());
        builder.setImplicitNamingStrategy(new ImplicitNamingStrategyComponentPathImpl());
        builder.addProperties(properties);
        return builder.buildSessionFactory();
    }

    @Bean
    public DataSource dataSource() throws PropertyVetoException {
        final var dataSource = new ComboPooledDataSource();
        dataSource.setProperties(new Properties());
        dataSource.setUser("sa");
        dataSource.setPassword("");
        dataSource.setDriverClass(org.h2.Driver.class.getName());
        dataSource.setJdbcUrl("jdbc:h2:mem:testdb");
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
