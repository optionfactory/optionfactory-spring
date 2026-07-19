package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.criteria.JoinType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface FilterGroup {

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(FilterGroup.Joins.class)
    public @interface Join {

        /**
         * the prefix path
         *
         * @return the prefix path for this group
         */
        String value();

        JoinType type() default JoinType.INNER;

        boolean reuse() default true;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Joins {

        Join[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(FilterGroup.Subselects.class)
    public @interface Subselect {

        /**
         * the prefix path
         *
         * @return the prefix path for this group
         */
        String value();

        boolean reuse() default true;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Subselects {

        Subselect[] value();
    }

}
