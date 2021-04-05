package net.optionfactory.spring.data.jpa.filtering.filters;

import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
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
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.PostgresFullTextSearch.PostgresFullTextSearchFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.PostgresFullTextSearch.RepeatablePostgresFullTextSearch;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(PostgresFullTextSearchFilter.class)
@Repeatable(RepeatablePostgresFullTextSearch.class)
public @interface PostgresFullTextSearch {

    String name();

    String[] paths();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatablePostgresFullTextSearch {

        PostgresFullTextSearch[] value();
    }

    public static class PostgresFullTextSearchFilter implements Filter {

        private final String name;
        private final List<Traversal> traversals;

        public PostgresFullTextSearchFilter(PostgresFullTextSearch annotation, EntityType<?> entityType) {
            this.name = annotation.name();
            this.traversals = Stream.of(annotation.paths())
                    .map(p -> Filters.traversal(annotation, entityType, p))
                    .collect(Collectors.toList());
            for (var traversal : traversals) {
                Filters.ensurePropertyOfAnyType(annotation, entityType, traversal, String.class);
            }
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            //params = alias, alias, alias, alias, query
            final List<Expression<?>> paths = traversals.stream().map(p -> Filters.path(root, p)).collect(Collectors.toList());
            final Expression<String> q = builder.literal(values[0]);
            final Expression<?>[] allargs = Stream.concat(paths.stream(), Stream.of(q)).toArray((size) -> new Expression[size]);
            Expression<Boolean> function = builder.function("fts", boolean.class, allargs);
            return builder.isTrue(function);
        }

        @Override
        public String name() {
            return name;
        }

    }

}
