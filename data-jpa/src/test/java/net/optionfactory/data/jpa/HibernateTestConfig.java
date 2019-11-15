package net.optionfactory.data.jpa;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;
import java.util.Properties;
import javax.sql.DataSource;
import net.optionfactory.data.jpa.filtering.Activity;
import net.optionfactory.data.jpa.filtering.EnableJpaWhitelistFilteringRepositories;
import net.optionfactory.data.jpa.hibernate.naming.LowerCaseAndUnderscoreNamingStrategy;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.hibernate.dialect.PostgreSQL10Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.orm.hibernate5.LocalSessionFactoryBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
//@EnableJpaRepositories(
//        basePackageClasses = Activity.class,
//        enableDefaultTransactions = false,
//        entityManagerFactoryRef = "hibernate",
//        repositoryBaseClass = JpaWhitelistFilteringRepositoryBase.class
//)
@EnableJpaWhitelistFilteringRepositories(
        basePackageClasses = Activity.class, 
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
        builder.scanPackages(Activity.class.getPackage().getName());
        builder.setPhysicalNamingStrategy(new LowerCaseAndUnderscoreNamingStrategy());
        builder.setImplicitNamingStrategy(new ImplicitNamingStrategyComponentPathImpl());
        return builder.buildSessionFactory();
    }

    @Bean
    public DataSource dataSource(
            @Value("${db.hostname}") String hostname,
            @Value("${db.port}") int port,
            @Value("${db.schema}") String schema,
            @Value("${db.username}") String username,
            @Value("${db.password}") String password) throws PropertyVetoException {
        final Properties driverProperties = new Properties();
        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setProperties(driverProperties);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setDriverClass(org.postgresql.Driver.class.getName());
        dataSource.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", hostname, port, schema));
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
    public JpaTransactionManager hibernateTx(SessionFactory hibernate) {
        return new JpaTransactionManager(hibernate);
    }

    @Bean
    public TransactionTemplate tt(JpaTransactionManager htt) {
        return new TransactionTemplate(htt);
    }

}
