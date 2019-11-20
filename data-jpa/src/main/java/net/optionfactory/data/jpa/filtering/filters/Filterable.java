package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.Filter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import net.optionfactory.data.jpa.filtering.filters.Filterable.RepeatableFilterable;
import org.springframework.core.annotation.AliasFor;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WhitelistedFilter(Filter.class)
@Repeatable(RepeatableFilterable.class)
public @interface Filterable {

    String name();

    @AliasFor(annotation = WhitelistedFilter.class, attribute = "value")
    Class<? extends Filter> filter();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableFilterable {

        Filterable[] value();
    }
}
