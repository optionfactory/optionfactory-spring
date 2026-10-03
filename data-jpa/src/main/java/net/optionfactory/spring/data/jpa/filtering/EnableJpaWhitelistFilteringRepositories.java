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
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

/// Enables spring data jpa repositories whose interfaces may also extend
/// [WhitelistFilteringRepository], by making [JpaWhitelistFilteringRepositoryBase] the base class
/// of every repository it creates.
///
/// It is `@EnableJpaRepositories` with that base class fixed and four defaults changed:
/// [#considerNestedRepositories()] is `true`, [#enableDefaultTransactions()] is `false`,
/// [#transactionManagerRef()] names a bean that is not meant to exist, and
/// [#repositoryFactoryBeanClass()] is [WhitelistFilteringRepositoryFactoryBean]. Repositories found
/// by this annotation therefore open no transaction of their own, and must run inside one
/// demarcated by the caller, typically a service: called without one, they fail with an
/// `IllegalTransactionStateException`.
///
/// ```java
/// @Configuration
/// @EnableJpaWhitelistFilteringRepositories(basePackageClasses = Pet.class)
/// public class JpaConfig {
/// }
///
/// @Entity
/// @TextCompare(name = "byName", path = "name")
/// @Sortable(name = "name", path = "name")
/// public class Pet { ... }
///
/// public interface PetRepository extends JpaRepository<Pet, Long>, WhitelistFilteringRepository<Pet> {
/// }
/// ```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableJpaRepositories(
        repositoryBaseClass = JpaWhitelistFilteringRepositoryBase.class
)
public @interface EnableJpaWhitelistFilteringRepositories {

    /// @return the packages to scan for repositories, an alias for [#basePackages()]
    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] value() default {};

    /// @return the packages to scan for repositories; the package of the annotated class when
    /// neither this nor [#basePackageClasses()] is set
    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] basePackages() default {};

    /// @return classes whose packages are scanned for repositories, a type-safe alternative to
    /// [#basePackages()]
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?>[] basePackageClasses() default {};

    /// @return the filters narrowing the scanned interfaces to the ones to be made repositories
    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] includeFilters() default {};

    /// @return the filters excluding scanned interfaces from becoming repositories
    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] excludeFilters() default {};

    /// @return the suffix of the class names looked up as custom repository implementation
    /// fragments
    @AliasFor(annotation = EnableJpaRepositories.class)
    String repositoryImplementationPostfix() default "Impl";

    /// @return the location of the named queries properties file; empty for spring data's default,
    /// `META-INF/jpa-named-queries.properties`
    @AliasFor(annotation = EnableJpaRepositories.class)
    String namedQueriesLocation() default "";

    /// @return how queries of query methods are resolved
    @AliasFor(annotation = EnableJpaRepositories.class)
    Key queryLookupStrategy() default Key.CREATE_IF_NOT_FOUND;

    /// Changed to [WhitelistFilteringRepositoryFactoryBean] from spring data's
    /// `JpaRepositoryFactoryBean`, which it extends: its repositories refuse to run outside a
    /// transaction. A replacement should extend it to keep that check.
    ///
    /// @return the factory bean creating each repository
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?> repositoryFactoryBeanClass() default WhitelistFilteringRepositoryFactoryBean.class;

    /// @return the generator of the repository bean names; `BeanNameGenerator` itself for the
    /// context default
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;

    /// @return the name of the `EntityManagerFactory` bean the repositories use
    @AliasFor(annotation = EnableJpaRepositories.class)
    String entityManagerFactoryRef() default "entityManagerFactory";

    /// The transaction manager used by the transactions the repositories open themselves.
    ///
    /// The default deliberately names a bean that is not expected to exist, rather than spring
    /// data's `transactionManager`: with [#enableDefaultTransactions()] off, it is only used by the
    /// methods a repository interface declares under a `@Transactional` without a qualifier, which
    /// then fail when invoked (with a `NoSuchBeanDefinitionException`) instead of silently opening a
    /// transaction of their own. Set it, together with [#enableDefaultTransactions()], to have the
    /// repositories demarcate transactions.
    ///
    /// @return the name of the transaction manager bean
    @AliasFor(annotation = EnableJpaRepositories.class)
    String transactionManagerRef() default "badIdeaTransactionManagerRef";

    /// Changed to `true` from spring data's default, so that repository interfaces nested in
    /// another type (an entity, a test class) are found as well.
    ///
    /// @return whether nested repository interfaces are discovered
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean considerNestedRepositories() default true;

    /// Changed to `false` from spring data's default: the `@Transactional` annotations of the
    /// repository implementation classes are ignored, so a repository method joins the caller's
    /// transaction, and fails when there is none (see [WhitelistFilteringRepositoryFactoryBean]).
    /// Only `@Transactional` annotations on the repository interfaces apply, to the methods those
    /// interfaces declare. Turning it on lets the repositories open their own transactions, as
    /// spring data's do, and lifts the check.
    ///
    /// @return whether the transactional defaults of the implementation classes apply
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean enableDefaultTransactions() default false;

    /// @return when the repositories are initialized during the context bootstrap
    @AliasFor(annotation = EnableJpaRepositories.class)
    BootstrapMode bootstrapMode() default BootstrapMode.DEFAULT;

    /// @return the character escaping `_` and `%` in derived `like` queries (`Containing`,
    /// `StartingWith`, `EndingWith`)
    @AliasFor(annotation = EnableJpaRepositories.class)
    char escapeCharacter() default '\\';

    /// @return the selector of the query enhancer used to introspect and rewrite string queries
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<? extends QueryEnhancerSelector> queryEnhancerSelector() default QueryEnhancerSelector.DefaultQueryEnhancerSelector.class;

}
