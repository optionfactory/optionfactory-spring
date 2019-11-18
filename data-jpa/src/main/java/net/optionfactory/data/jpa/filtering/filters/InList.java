package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.InList.InListFilter;
import net.optionfactory.data.jpa.filtering.filters.InList.RepeatableInList;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;

@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InListFilter.class)
@Repeatable(RepeatableInList.class)
public @interface InList {

    String name();

    String property();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInList {

        InList[] value();
    }

    public static class InListFilter implements Filter {

        private final String name;
        private final String property;

        public InListFilter(InList il, EntityType<?> entityType) {
            this.name = il.name();
            this.property = il.property();
            Filters.ensurePropertyOfAnyType(il, entityType, property, String.class, Number.class);
        }

        @Override
        public Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values) {
            //TODO: handle numbers
            return builder.in(Filters.traverseProperty(root, property)).in((Object[]) values);
        }

        @Override
        public String name() {
            return name;
        }

    }

}
