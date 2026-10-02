package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InList.InListFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InList.RepeatableInList;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter keeping the rows whose property is one of a set of accepted values.
///
/// The property must be a [String], a [Number] or a primitive other than `boolean` (see
/// [BooleanCompare] for those). The values are converted to the property type, a value that cannot
/// be converted being rejected; a `char` property takes single-character values. No value at all
/// matches no row, and a `null` value matches the rows where the property is `NULL`.
///
/// ```java
/// @Entity
/// @InList(name = "byBreed", path = "breed")
/// public class Pet { ... }
///
/// FilterRequest.builder().inList("byBreed", "labrador", "beagle").build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InListFilter.class)
@Repeatable(RepeatableInList.class)
public @interface InList {

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the dot-separated path of the filtered property, from the entity
    String path();

    /// The quantifier applied when [#path()] crosses a collection: whether a row is kept because
    /// *some* element matches ([Match#ANY]) or because *no* element does ([Match#NONE]). A negated
    /// filter over a collection is `NONE` over a positive condition, never `ANY` over a negated one.
    /// Required when the path crosses a collection, where leaving it [Match#UNSTATED] is rejected
    /// when the repository is built; unnecessary when it crosses none, where `ANY` and `UNSTATED`
    /// read alike and `NONE` is rejected.
    ///
    /// @return the quantifier
    Match match() default Match.UNSTATED;

    /// The container of repeated [InList] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInList {

        /// @return the repeated annotations
        InList[] value();
    }

    /// The filter whitelisted by [InList].
    public static class InListFilter implements TraversalFilter<Object> {

        private final String name;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a property of a supported type, or misuses [Match]
        public InListFilter(InList annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, String.class, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
        }

        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when a
        /// value cannot be converted to the property type
        @Override
        public Predicate condition(Root<?> root, Path<Object> path, CriteriaBuilder builder, String[] values) {
            if (values.length == 0) {
                return builder.disjunction();
            }
            final Object[] nonNullValues = Stream.of(values)
                    .filter(Objects::nonNull)
                    .map(value -> Values.convert(name, root, value, traversal.attribute().getJavaType()))
                    .toArray();
            final boolean hasNullValues = nonNullValues.length < values.length;
            if (hasNullValues) {
                return builder.or(path.isNull(), path.in(nonNullValues));
            }
            return path.in(nonNullValues);
        }

        /// @return the name the filter is whitelisted under
        @Override
        public String name() {
            return name;
        }

        /// @return the resolved path of the filtered property
        @Override
        public Traversal traversal() {
            return traversal;
        }
    }

    /// Encodes the values of an [InList] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    public enum Filter {
        /// The only instance.
        INSTANCE;

        /// @param values the accepted values, in their string form; a `null` one requests the
        /// `NULL` rows
        /// @return the same array
        public String[] in(String... values) {
            return values;
        }

    }

}
