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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.NumberCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare.RepeatableNumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Values;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter comparing a numeric property with one value, or two for
/// [Operator#BETWEEN].
///
/// The property is a numeric primitive or a concrete [Number] (boxed primitives, [BigInteger],
/// [BigDecimal], ...); a `char` is not numeric, and is rejected when the repository is built. The
/// first filter value is a whitelisted [Operator], followed by the operands, which are converted to
/// the property type: a value that cannot be converted, or does not fit the type, is rejected.
/// Only `EQ` and `NEQ` accept a `null` value, to compare with `NULL`.
///
/// ```java
/// @Entity
/// @NumberCompare(name = "byWeight", path = "weight")
/// public class Pet { ... }
///
/// FilterRequest.builder().number("byWeight", f -> f.between(1, 5)).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(NumberCompareFilter.class)
@Repeatable(RepeatableNumberCompare.class)
public @interface NumberCompare {

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

    /// @return the operators a client may request; an empty array is rejected when the repository
    /// is built
    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN
    };

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

    /// The container of repeated [NumberCompare] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableNumberCompare {

        /// @return the repeated annotations
        NumberCompare[] value();
    }

    /// The filter whitelisted by [NumberCompare].
    public static class NumberCompareFilter implements TraversalFilter<Number> {

        private final String name;
        private final EnumSet<Operator> operators;
        private final Class<? extends Number> propertyClass;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a numeric property (a `char` is not one), misuses [Match], or
        /// whitelists no operator
        @SuppressWarnings("unchecked")
        public NumberCompareFilter(NumberCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            this.propertyClass = (Class<? extends Number>) Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, Number.class, byte.class, short.class, int.class, long.class, float.class, double.class);
            Filters.ensureConfiguration(annotation.operators().length > 0, annotation.name(), entity, "operators must not be empty");
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when
        /// the operator is missing, unknown or not whitelisted, the number of values does not fit
        /// it, a value cannot be converted, or a `null` value is given to an operator other than
        /// `EQ` and `NEQ`
        @Override
        public Predicate condition(Root<?> root, Path<Number> lhs, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length > 0, root, name, "missing operator");
            final Operator operator = Filters.parseEnum(root, name, "operator", Operator.class, values[0]);
            Filters.ensure(operators.contains(operator), root, name, "operator %s not whitelisted (%s)", operator, operators);
            Filters.ensure(values.length == (operator == Operator.BETWEEN ? 3 : 2), root, name, "unexpected number of values: %d", values.length);
            final String value = values[1];
            final Number rhs = (Number) Values.convert(name, root, value, propertyClass);
            return switch (operator) {
                case EQ ->
                    rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ ->
                    rhs == null ? lhs.isNotNull() : builder.or(lhs.isNull(), builder.notEqual(lhs, rhs));
                case LT -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.lt(lhs, rhs);
                }
                case GT -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.gt(lhs, rhs);
                }
                case LTE -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.le(lhs, rhs);
                }
                case GTE -> {
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    yield builder.ge(lhs, rhs);
                }
                case BETWEEN -> {
                    final String value2 = values[2];
                    final Number rhs2 = (Number) Values.convert(name, root, value2, propertyClass);
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, root, name, "value2 cannot be null for operator %s", operator);
                    final Number[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new Number[l]);
                    yield builder.and(builder.ge(lhs, sorted[0]), builder.le(lhs, sorted[1]));
                }
                default ->
                    throw new IllegalStateException("unreachable");
            };
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

    /// Encodes the values of a [NumberCompare] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    ///
    /// Numbers are encoded with their `toString`, `null` as `null`.
    public enum Filter {
        /// The only instance.
        INSTANCE;

        private static String str(Number n) {
            return n == null ? null : n.toString();
        }

        /// @param op the operator
        /// @param values the operands
        /// @return the filter values
        public String[] of(Operator op, Number... values) {
            return Stream.concat(
                    Stream.of(op.name()),
                    Stream.of(values).map(v -> str(v))
            ).toArray(i -> new String[i]);
        }

        /// @param op the operator
        /// @param values the operands, already encoded
        /// @return the filter values
        public String[] of(Operator op, String... values) {
            return Stream.concat(
                    Stream.of(op.name()),
                    Stream.of(values)
            ).toArray(i -> new String[i]);
        }

        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(Number value) {
            return new String[]{Operator.EQ.name(), str(value)};
        }

        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(Number value) {
            return new String[]{Operator.NEQ.name(), str(value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lt(Number value) {
            return new String[]{Operator.LT.name(), str(value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gt(Number value) {
            return new String[]{Operator.GT.name(), str(value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lte(Number value) {
            return new String[]{Operator.LTE.name(), str(value)};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gte(Number value) {
            return new String[]{Operator.GTE.name(), str(value)};
        }

        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(Number value1, Number value2) {
            return new String[]{Operator.BETWEEN.name(), str(value1), str(value2)};
        }

    }

}
