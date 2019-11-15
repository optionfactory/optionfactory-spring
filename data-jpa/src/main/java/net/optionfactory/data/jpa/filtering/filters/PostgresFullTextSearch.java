package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.filtering.filters.spi.WhitelistedFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.PostgresFullTextSearch.PostgresFullTextSearchFilter;
import net.optionfactory.data.jpa.filtering.filters.PostgresFullTextSearch.RepeatablePostgresFullTextSearch;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;


@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(PostgresFullTextSearchFilter.class)
@Repeatable(RepeatablePostgresFullTextSearch.class)
public @interface PostgresFullTextSearch {
    
    String name();

    String[] properties();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatablePostgresFullTextSearch {

        PostgresFullTextSearch[] value();
    }

    public static class PostgresFullTextSearchFilter implements Filter {

        private final String name;
        private final String[] properties;

        public PostgresFullTextSearchFilter(PostgresFullTextSearch ie, EntityType<?> entityType) {
            this.name = ie.name();
            this.properties = ie.properties();
            for (String property : properties) {
                Filters.ensurePropertyOfAnyType(ie, entityType, property, String.class);            
            }
        }

        @Override
        public Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values) {
            //params = alias, alias, alias, alias, query
            final List<Expression<?>> paths = Stream.of(properties).map(p -> root.get(p)).collect(Collectors.toList());
            final Expression<String> query = builder.literal(values[0]);
            final Expression<?>[] allargs = Stream.concat(paths.stream(), Stream.of(query)).toArray((size) -> new Expression[size]);
            Expression<Boolean> function = builder.function("fts", boolean.class, allargs);
            return builder.isTrue(function);
        }

        @Override
        public String name() {
            return name;
        }

    }

}
