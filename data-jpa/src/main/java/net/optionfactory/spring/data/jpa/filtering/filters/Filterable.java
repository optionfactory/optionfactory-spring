package net.optionfactory.spring.data.jpa.filtering.filters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable.RepeatableFilterable;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.CustomFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import org.springframework.core.annotation.AliasFor;

/// Whitelists a custom [Filter] implementation on an entity, under a name.
///
/// The filter is instantiated once per repository, when the repository is created, through its
/// only public constructor whose parameters are all among: this annotation, the
/// `JpaEntityInformation`, the `EntityManager`, the `EntityManagerFactory` and the `EntityType` of
/// the entity. No such constructor, or more than one, fails the repository creation with an
/// `IllegalStateException`; a runtime exception thrown by the constructor is propagated as is. Such
/// filters may extend [CustomFilter], which takes care of the name.
///
/// ```java
/// @Entity
/// @Filterable(name = "nearby", filter = NearbyFilter.class)
/// public class Shop { ... }
/// ```
///
/// This annotation carries no `path` and therefore no [Match] quantifier: the filter owns whatever
/// it traverses, so it owns the quantifier too. A custom filter reaching through a collection should
/// implement [net.optionfactory.spring.data.jpa.filtering.TraversalFilter] and build its traversal
/// with a quantifier (`Filters.traversal(entity, name, path, Match.NONE)`); the repository then
/// folds and negates it exactly like a built-in filter, grouping it with any other filter reaching
/// the same collection with the same quantifier. A custom filter implementing [Filter] directly
/// receives the `CriteriaQuery` and builds whatever subquery it needs itself.
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WhitelistedFilter(Filter.class)
@Repeatable(RepeatableFilterable.class)
public @interface Filterable {

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the filter implementation
    @AliasFor(annotation = WhitelistedFilter.class, attribute = "value")
    Class<? extends Filter> filter();

    /// The container of repeated [Filterable] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableFilterable {

        /// @return the repeated annotations
        Filterable[] value();
    }
}
