package net.optionfactory.spring.data.jpa.filtering.filters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable.RepeatableSortable;

/**
 * Whitelists a sorter.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RepeatableSortable.class)
public @interface Sortable {

    /**
     * The sortable name.
     *
     * @return
     */
    String name();

    /**
     * The whitelisted path.
     *
     * @return
     */
    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableSortable {

        Sortable[] value();
    }
}
