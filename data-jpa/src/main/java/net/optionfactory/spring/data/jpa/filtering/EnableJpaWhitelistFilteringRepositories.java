package net.optionfactory.spring.data.jpa.filtering;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.query.QueryEnhancerSelector;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

/**
 * Enable use of JPA repositories extending the
 * {@link WhitelistFilteringRepository} interface.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableJpaRepositories(
        repositoryBaseClass = JpaWhitelistFilteringRepositoryBase.class
)
public @interface EnableJpaWhitelistFilteringRepositories {

    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] value() default {};

    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] basePackages() default {};

    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?>[] basePackageClasses() default {};

    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] includeFilters() default {};

    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] excludeFilters() default {};

    @AliasFor(annotation = EnableJpaRepositories.class)
    String repositoryImplementationPostfix() default "Impl";

    @AliasFor(annotation = EnableJpaRepositories.class)
    String namedQueriesLocation() default "";

    @AliasFor(annotation = EnableJpaRepositories.class)
    Key queryLookupStrategy() default Key.CREATE_IF_NOT_FOUND;

    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?> repositoryFactoryBeanClass() default JpaRepositoryFactoryBean.class;

    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;

    @AliasFor(annotation = EnableJpaRepositories.class)
    String entityManagerFactoryRef() default "entityManagerFactory";

    @AliasFor(annotation = EnableJpaRepositories.class)
    String transactionManagerRef() default "badIdeaTransactionManagerRef";

    /**
     * Default has been changed to true.
     *
     * @return true
     */
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean considerNestedRepositories() default true;

    /**
     * Default has been changed to false.
     *
     * @return false
     */
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean enableDefaultTransactions() default false;

    @AliasFor(annotation = EnableJpaRepositories.class)
    BootstrapMode bootstrapMode() default BootstrapMode.DEFAULT;

    @AliasFor(annotation = EnableJpaRepositories.class)
    char escapeCharacter() default '\\';

    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<? extends QueryEnhancerSelector> queryEnhancerSelector() default QueryEnhancerSelector.DefaultQueryEnhancerSelector.class;

}
