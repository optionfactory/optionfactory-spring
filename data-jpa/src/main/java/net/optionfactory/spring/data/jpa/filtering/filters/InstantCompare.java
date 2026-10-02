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
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.InstantCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare.RepeatableInstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter comparing an [Instant] property with one value, or two for
/// [Operator#BETWEEN].
///
/// The first filter value is a whitelisted [Operator], followed by the instants in the configured
/// [#format()]; a value that cannot be parsed is rejected. Only `EQ` and `NEQ` accept a `null`
/// value, to compare with `NULL`.
///
/// ```java
/// @Entity
/// @InstantCompare(name = "createdAt", path = "createdAt", format = Format.UNIX_MS)
/// public class Order { ... }
///
/// FilterRequest.builder().instant("createdAt", f -> f.gte(Format.UNIX_MS, since)).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(InstantCompareFilter.class)
@Repeatable(RepeatableInstantCompare.class)
public @interface InstantCompare {

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

    /// How the instants are written in the filter values.
    public enum Format {
        /// An ISO-8601 instant in UTC, as [Instant#parse] reads it, e.g. `2020-01-01T10:00:00Z`.
        ISO_8601,
        /// Decimal seconds since the epoch.
        UNIX_S,
        /// Decimal milliseconds since the epoch.
        UNIX_MS,
        /// Decimal nanoseconds since the epoch.
        UNIX_NS;
    }

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the operators a client may request; an empty array is rejected when the repository
    /// is built
    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

    /// @return the format of the instants in the filter values
    Format format() default Format.ISO_8601;

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

    /// The container of repeated [InstantCompare] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableInstantCompare {

        /// @return the repeated annotations
        InstantCompare[] value();
    }

    /// The filter whitelisted by [InstantCompare].
    public static class InstantCompareFilter implements TraversalFilter<Instant> {

        private final String name;
        private final EnumSet<Operator> operators;
        private final Format format;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to an `Instant` property, misuses [Match], or whitelists no operator
        public InstantCompareFilter(InstantCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, Instant.class);
            Filters.ensureConfiguration(annotation.operators().length > 0, annotation.name(), entity, "operators must not be empty");
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.format = annotation.format();
        }

        /// @throws InvalidFilterRequest when the operator is missing, unknown or not whitelisted,
        /// the number of values does not fit it, a value cannot be parsed, or a `null` value is
        /// given to an operator other than `EQ` and `NEQ`
        @Override
        public Predicate condition(Root<?> root, Path<Instant> lhs, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length > 0, root, name, "missing operator");
            final Operator operator = Filters.parseEnum(root, name, "operator", Operator.class, values[0]);
            Filters.ensure(operators.contains(operator), root, name, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), root, name, "unexpected number of values: %d", values.length);
            final String value = values[1];
            final Instant rhs = parseInstant(root, value);
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
                    final Instant rhs2 = parseInstant(root, value2);
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, root, name, "value2 cannot be null for operator %s", operator);
                    final Instant[] instants = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Instant[l]);
                    yield builder.and(builder.greaterThanOrEqualTo(lhs, instants[0]), builder.lessThanOrEqualTo(lhs, instants[1]));
                }
                default ->
                    throw new IllegalStateException("unreachable");
            };
        }

        private Instant parseInstant(Root<?> root, String value) {
            if (value == null) {
                return null;
            }
            try {
                return switch (format) {
                    case ISO_8601 ->
                        Instant.parse(value);
                    case UNIX_S ->
                        Instant.ofEpochSecond(Long.parseLong(value, 10));
                    case UNIX_MS ->
                        Instant.ofEpochMilli(Long.parseLong(value, 10));
                    case UNIX_NS -> {
                        final BigInteger nanoseconds = new BigInteger(value, 10);
                        final BigInteger[] secondsAndNanosecondsFraction = nanoseconds.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
                        yield Instant.ofEpochSecond(secondsAndNanosecondsFraction[0].longValueExact(), secondsAndNanosecondsFraction[1].longValueExact());
                    }
                    default ->
                        throw new IllegalStateException("unreachable");
                };
            } catch (DateTimeException | ArithmeticException | NumberFormatException ex) {
                throw new InvalidFilterRequest(name, root, String.format("cannot parse '%s' as an instant in format %s: %s", value, format, ex.getMessage()));
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

    /// Encodes the values of an [InstantCompare] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    ///
    /// The format passed must be the one the filter is configured with; `null` instants are encoded
    /// as `null`.
    public enum Filter {
        /// The only instance.
        INSTANCE;

        private String str(Format format, Instant value) {
            if (value == null) {
                return null;
            }
            return switch (format) {
                case ISO_8601 ->
                    value.toString();
                case UNIX_S ->
                    Long.toString(value.getEpochSecond());
                case UNIX_MS ->
                    Long.toString(value.toEpochMilli());
                case UNIX_NS ->
                    BigInteger.valueOf(value.getEpochSecond())
                    .multiply(BigInteger.valueOf(1_000_000_000))
                    .add(BigInteger.valueOf(value.getNano()))
                    .toString();
                default ->
                    throw new IllegalStateException("unreachable");
            };
        }

        /// @param op the operator
        /// @param format the format of the filter
        /// @param values the operands
        /// @return the filter values
        public String[] of(Operator op, Format format, Instant... values) {
            return Stream.concat(
                    Stream.of(op.name()),
                    Stream.of(values).map(v -> str(format, v))
            ).toArray(i -> new String[i]);
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

        /// @param format the format of the filter
        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(Format format, Instant value) {
            return new String[]{Operator.EQ.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(Format format, Instant value) {
            return new String[]{Operator.NEQ.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] lt(Format format, Instant value) {
            return new String[]{Operator.LT.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] gt(Format format, Instant value) {
            return new String[]{Operator.GT.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] lte(Format format, Instant value) {
            return new String[]{Operator.LTE.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value the operand
        /// @return the filter values
        public String[] gte(Format format, Instant value) {
            return new String[]{Operator.GTE.name(), str(format, value)};
        }

        /// @param format the format of the filter
        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(Format format, Instant value1, Instant value2) {
            return new String[]{Operator.BETWEEN.name(), str(format, value1), str(format, value2)};
        }

    }

}
