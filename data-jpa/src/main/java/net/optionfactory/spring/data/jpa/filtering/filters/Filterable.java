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

/**
 * Whitelists a custom filter implementation. Such filters could extend the
 * {@link CustomFilter} base class.
 *
 * <p>
 * This annotation carries no {@code path} and therefore no {@link Match}
 * quantifier: the filter owns whatever it traverses, so it owns the quantifier
 * too. A custom filter reaching through a collection should implement
 * {@link net.optionfactory.spring.data.jpa.filtering.TraversalFilter} and build
 * its traversal with a quantifier
 * ({@code Filters.traversal(entity, name, path, Match.NONE)}); the adapter then
 * folds and negates it exactly like a built-in filter, grouping it with any
 * other filter reaching the same collection with the same quantifier. A custom
 * filter implementing {@link net.optionfactory.spring.data.jpa.filtering.Filter}
 * directly receives the {@code CriteriaQuery} and builds whatever subquery it
 * needs itself.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WhitelistedFilter(Filter.class)
@Repeatable(RepeatableFilterable.class)
public @interface Filterable {

    /**
     * The filter name.
     * @return the name
     */
    String name();

    /**
     * The filter implementation type.
     * @return the type
     */
    @AliasFor(annotation = WhitelistedFilter.class, attribute = "value")
    Class<? extends Filter> filter();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableFilterable {

        Filterable[] value();
    }
}
