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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.InEnumFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum.RepeatableInEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter keeping the rows whose enum property is one of a set of accepted constants.
///
/// The filter values are the names of the accepted constants of [#type()]; an unknown name is
/// rejected. No value at all matches no row. A `null` value matches the rows where the property is
/// `NULL`, and is only accepted when the filter is [#nullable()].
///
/// ```java
/// @Entity
/// @InEnum(name = "byType", path = "type", type = PetType.class)
/// public class Pet { ... }
///
/// FilterRequest.builder().inEnum("byType", PetType.DOG, PetType.CAT).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InEnumFilter.class)
@Repeatable(RepeatableInEnum.class)
public @interface InEnum {

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the enum type of the property, which the values are parsed as
    Class<? extends Enum<?>> type();

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
    /// @return whether a client may request the rows where the property is `NULL`, by sending a
    /// `null` value
    boolean nullable() default false;

    /// The container of repeated [InEnum] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInEnum {

        /// @return the repeated annotations
        InEnum[] value();
    }

    /// The filter whitelisted by [InEnum].
    public static class InEnumFilter implements TraversalFilter<Enum<?>> {

        private final String name;
        private final boolean nullable;
        private final Class<? extends Enum> type;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a property of the configured enum type, or misuses [Match]
        public InEnumFilter(InEnum annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.nullable = annotation.nullable();
            this.type = annotation.type();
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, type);
        }

        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when a
        /// value names no constant of the enum, or is `null` and the filter is not nullable
        @Override
        public Predicate condition(Root<?> root, Path<Enum<?>> path, CriteriaBuilder builder, String[] values) {
            final boolean hasNull = Stream.of(values).anyMatch(Objects::isNull);
            Filters.ensure(!hasNull || nullable, root, name, "null enum filter values is not whitelisted");
            @SuppressWarnings("unchecked")
            final Set<Enum> requested = Stream.of(values)
                    .filter(Objects::nonNull)
                    .map(value -> Filters.parseEnum(root, name, "value", type, value))
                    .collect(Collectors.toSet());
            if (requested.isEmpty()) {
                return hasNull ? path.isNull() : builder.disjunction();
            }
            return hasNull ? builder.or(path.isNull(), path.in(requested)) : path.in(requested);
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

    /// Encodes the values of an [InEnum] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    public enum Filter {
        /// The only instance.
        INSTANCE;

        /// @param values the accepted constants; a `null` one requests the `NULL` rows
        /// @return the constant names, with `null`s kept
        public String[] in(Enum<?>... values) {
            return Stream.of(values)
                    .map(ev -> ev == null ? null : ev.name())
                    .toArray(i -> new String[i]);
        }

    }

}
