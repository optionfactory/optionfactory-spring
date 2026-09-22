package net.optionfactory.spring.data.jpa.filtering.mysql;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = HibernateOnMysqlTestConfig.class)
public class HibernateOnMysqlTestConfig {

    /**
     * Mysql shared by every test declaring {@code @SharedContainer(Mysql.class)}: started before the first
     * one, stopped after the last one. The image boots with an insecure root that exists only on localhost,
     * so the tcp-reachable user has to be created before anything connects; the port only opens after
     * initialization completes, the initialize-phase server being skip-networking.
     */
    public static class Mysql implements ContainerDefinition<GenericContainer> {

        private static final String USERNAME = "test";
        private static final String PASSWORD = "test";
        private static final String DATABASE = "test";

        @Override
        public GenericContainer start() throws Exception {
            final var image = DockerImageName.parse("optionfactory/debian13-mysql8:240");
            final var container = new GenericContainer<>(image)
                    .withExposedPorts(3306)
                    // the datadir is a tmpfs for speed: mysqld runs as a non-root user, so it must be world-writable
                    .withTmpFs(Map.of("/var/lib/mysql", "rw,mode=1777"))
                    .waitingFor(Wait.forListeningPort());
            container.start();
            container.execInContainer("mysql", "--protocol=socket", "-uroot", "-e", "CREATE USER '%s'@'%%' IDENTIFIED BY '%s'".formatted(USERNAME, PASSWORD));
            container.execInContainer("mysql", "--protocol=socket", "-uroot", "-e", "GRANT ALL PRIVILEGES ON *.* TO '%s'@'%%' WITH GRANT OPTION".formatted(USERNAME));
            container.execInContainer("mysql", "--protocol=socket", "-uroot", "-e", "CREATE DATABASE %s".formatted(DATABASE));
            return container;
        }

        @Override
        public Map<String, Object> properties(GenericContainer container) {
            return Map.of(
                    "db.jdbc.url", "jdbc:mysql://%s:%d/%s?sslMode=DISABLED&allowPublicKeyRetrieval=true".formatted(container.getHost(), container.getMappedPort(3306), DATABASE),
                    "db.username", USERNAME,
                    "db.password", PASSWORD
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
        factory.setPackagesToScan(HibernateOnMysqlTestConfig.class.getPackage().getName());
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
