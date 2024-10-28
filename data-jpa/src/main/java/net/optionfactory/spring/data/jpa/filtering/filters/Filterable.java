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
