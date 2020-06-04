package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.optionfactory.spring.data.jpa.filtering.Filter;

/**
 * Meta-annotation to be applied on filter annotations.
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WhitelistedFilter {

    /**
     * The {@link Filter} implementation type.
     */
    Class<? extends Filter> value();
}
