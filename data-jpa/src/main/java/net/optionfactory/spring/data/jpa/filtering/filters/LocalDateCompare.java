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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare.LocalDateCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare.RepeatableLocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter comparing a [LocalDate] property with one value, or two for
/// [Operator#BETWEEN].
///
/// The first filter value is a whitelisted [Operator], followed by the dates formatted with the
/// configured [#datePattern()]; a value that cannot be parsed is rejected. Only `EQ` and `NEQ`
/// accept a `null` value, to compare with `NULL`.
///
/// ```java
/// @Entity
/// @LocalDateCompare(name = "bornAfter", path = "birthDate", operators = Operator.GT)
/// public class Pet { ... }
///
/// FilterRequest.builder().localDate("bornAfter", f -> f.gt(LocalDate.of(2020, 1, 1))).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(LocalDateCompareFilter.class)
@Repeatable(RepeatableLocalDateCompare.class)
public @interface LocalDateCompare {

    /// The comparison requested by the client, as the first filter value.
    public enum Operator {
        /// The property equals the value; a `null` value matches the `NULL` rows.
        EQ,
        /// The property differs from the value, `NULL` rows included; a `null` value matches the
        /// rows that are not `NULL`.
        NEQ,
        /// The property is less than the value.
        LT,
        /// The property is greater than the value.
        GT,
        /// The property is less than or equal to the value.
        LTE,
        /// The property is greater than or equal to the value.
        GTE,
        /// The property lies between two values, both included, in either order.
        BETWEEN;
    }

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the operators a client may request; must not be empty
    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    /// @return the `DateTimeFormatter` pattern of the dates in the filter values
    String datePattern() default "yyyy-MM-dd";

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

    /// The container of repeated [LocalDateCompare] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableLocalDateCompare {

        /// @return the repeated annotations
        LocalDateCompare[] value();
    }

    /// The filter whitelisted by [LocalDateCompare].
    public static class LocalDateCompareFilter implements TraversalFilter<LocalDate> {

        private final String name;
        private final EnumSet<Operator> operators;
        private final DateTimeFormatter formatter;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a `LocalDate` property, or misuses [Match]
        /// @throws IllegalArgumentException when the date pattern is invalid
        public LocalDateCompareFilter(LocalDateCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, LocalDate.class);
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.formatter = DateTimeFormatter.ofPattern(annotation.datePattern());
        }

        /// @throws InvalidFilterRequest when the operator is missing, unknown or not whitelisted,
        /// the number of values does not fit it, a value cannot be parsed, or a `null` value is
        /// given to an operator other than `EQ` and `NEQ`
        @Override
        public Predicate condition(Root<?> root, Path<LocalDate> lhs, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length > 0, root, name, "missing operator");
            final Operator operator = Filters.parseEnum(root, name, "operator", Operator.class, values[0]);
            Filters.ensure(operators.contains(operator), root, name, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), root, name, "unexpected number of values: %d", values.length);
            final String value = values[1];
            final LocalDate rhs = parseLocalDate(root, value);
            return switch (operator) {
                case EQ ->
                    rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ ->
                    rhs == null ? lhs.isNotNull() : builder.or(lhs.isNull(), builder.notEqual(lhs, rhs));
                case LT -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.lessThan(lhs, rhs);
                }
                case GT -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.greaterThan(lhs, rhs);
                }
                case LTE -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.lessThanOrEqualTo(lhs, rhs);
                }
                case GTE -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.greaterThanOrEqualTo(lhs, rhs);
                }
                case BETWEEN -> {
                    final String value2 = values[2];
                    final LocalDate rhs2 = parseLocalDate(root, value2);
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, root, name, "value2 cannot be null for operator %s", operator);
                    final LocalDate[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new LocalDate[l]);
                    yield builder.and(builder.greaterThanOrEqualTo(lhs, sorted[0]), builder.lessThanOrEqualTo(lhs, sorted[1]));
                }
                default ->
                    throw new IllegalStateException("unreachable");
            };
        }

        private LocalDate parseLocalDate(Root<?> root, String value) {
            if (value == null) {
                return null;
            }
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeException ex) {
                throw new InvalidFilterRequest(name, root, String.format("cannot parse '%s' as a local date: %s", value, ex.getMessage()));
            }
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

    /// Encodes the values of a [LocalDateCompare] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    ///
    /// The overloads without a pattern format the dates as `yyyy-MM-dd`, the filter's default; the
    /// others take the pattern the filter is configured with. `null` dates are encoded as `null`.
    public enum Filter {
        /// The only instance.
        INSTANCE;

        private static String str(String format, LocalDate value) {
            if (value == null) {
                return null;
            }
            return value.format(DateTimeFormatter.ofPattern(format));
        }

        private static final String DEFAULT_FORMAT = "yyyy-MM-dd";

        /// @param op the operator
        /// @param format the date pattern of the filter
        /// @param values the operands
        /// @return the filter values
        public static String[] of(Operator op, String format, LocalDate... values) {
            return Stream.concat(
                    Stream.of(op.name()),
                    Stream.of(values).map(v -> str(format, v))
            ).toArray(i -> new String[i]);
        }

        /// @param op the operator
        /// @param values the operands
        /// @return the filter values
        public String[] of(Operator op, LocalDate... values) {
            return of(op, DEFAULT_FORMAT, values);
        }

        /// @param op the operator
        /// @param values the operands, already formatted
        /// @return the filter values
        public String[] of(Operator op, String... values) {
            return Stream.concat(
                    Stream.of(op.name()),
                    Stream.of(values)
            ).toArray(i -> new String[i]);
        }

        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(LocalDate value) {
            return new String[]{Operator.EQ.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(String format, LocalDate value) {
            return new String[]{Operator.EQ.name(), str(format, value)};
        }

        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(LocalDate value) {
            return new String[]{Operator.NEQ.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(String format, LocalDate value) {
            return new String[]{Operator.NEQ.name(), str(format, value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lt(LocalDate value) {
            return new String[]{Operator.LT.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] lt(String format, LocalDate value) {
            return new String[]{Operator.LT.name(), str(format, value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gt(LocalDate value) {
            return new String[]{Operator.GT.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] gt(String format, LocalDate value) {
            return new String[]{Operator.GT.name(), str(format, value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lte(LocalDate value) {
            return new String[]{Operator.LTE.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] lte(String format, LocalDate value) {
            return new String[]{Operator.LTE.name(), str(format, value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gte(LocalDate value) {
            return new String[]{Operator.GTE.name(), str(DEFAULT_FORMAT, value)};
        }

        /// @param format the date pattern of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] gte(String format, LocalDate value) {
            return new String[]{Operator.GTE.name(), str(format, value)};
        }

        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(LocalDate value1, LocalDate value2) {
            return new String[]{Operator.BETWEEN.name(), str(DEFAULT_FORMAT, value1), str(DEFAULT_FORMAT, value2)};
        }

        /// @param format the date pattern of the filter
        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(String format, LocalDate value1, LocalDate value2) {
            return new String[]{Operator.BETWEEN.name(), str(format, value1), str(format, value2)};
        }

    }

}
