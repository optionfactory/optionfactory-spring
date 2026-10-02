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
import java.util.EnumSet;
import java.util.Set;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare.BooleanCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare.RepeatableBooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/// Whitelists a filter comparing a `boolean` or `Boolean` property with a truth value.
///
/// The filter takes two values: a whitelisted [Operator] and the truth value, which must be exactly
/// (case included) [#trueValue()] or [#falseValue()], letting a client speak its own vocabulary
/// (`yes`/`no`, `Y`/`N`). A `null` truth value compares with `NULL`: `EQ` matches the rows where
/// the property is `NULL`, `NEQ` those where it is not. A non-null truth value is rendered as an
/// `IS TRUE`/`IS FALSE` test, so a `NULL` property matches neither `EQ` nor `NEQ`.
///
/// ```java
/// @Entity
/// @BooleanCompare(name = "active", path = "active")
/// public class Account { ... }
///
/// FilterRequest.builder().bool("active", f -> f.eq(true)).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(BooleanCompareFilter.class)
@Repeatable(RepeatableBooleanCompare.class)
public @interface BooleanCompare {

    /// The comparison requested by the client, as the first filter value.
    public enum Operator {
        /// The property equals the truth value.
        EQ,
        /// The property differs from the truth value.
        NEQ;
    }

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the operators a client may request; an empty array is rejected when the repository
    /// is built
    Operator[] operators() default {
        Operator.EQ, Operator.NEQ
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
    /// @return the value a client sends for `true`
    String trueValue() default "true";

    /// @return the value a client sends for `false`
    String falseValue() default "false";

    /// The container of repeated [BooleanCompare] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableBooleanCompare {

        /// @return the repeated annotations
        BooleanCompare[] value();
    }

    /// The filter whitelisted by [BooleanCompare].
    public static class BooleanCompareFilter implements TraversalFilter<Boolean> {

        private final String name;
        private final EnumSet<Operator> operators;
        private final String trueValue;
        private final Set<String> validValues;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a boolean property, misuses [Match], or whitelists no operator
        public BooleanCompareFilter(BooleanCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.trueValue = annotation.trueValue();
            this.validValues = Set.of(annotation.trueValue(), annotation.falseValue());
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), this.traversal, Boolean.class, boolean.class);
            Filters.ensureConfiguration(annotation.operators().length > 0, annotation.name(), entity, "operators must not be empty");
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
        }

        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when
        /// the values are not an operator and a truth value, the operator is unknown or not
        /// whitelisted, or the truth value is neither of the configured ones
        @Override
        public Predicate condition(Root<?> root, Path<Boolean> path, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 2, root, name, "expected operator and value, got %d values", values.length);
            final Operator operator = Filters.parseEnum(root, name, "operator", Operator.class, values[0]);
            Filters.ensure(operators.contains(operator), root, name, "operator %s not whitelisted (%s)", operator, operators);
            final String value = values[1];
            if (value == null) {
                return operator == Operator.EQ ? path.isNull() : path.isNotNull();
            }
            Filters.ensure(validValues.contains(value), root, name, "value does not match valid values: %s", validValues);
            return operator == Operator.EQ == trueValue.equals(value) ? builder.isTrue(path) : builder.isFalse(path);
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

    /// Encodes the values of a [BooleanCompare] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    ///
    /// The `Boolean` overloads encode the default `true`/`false` truth values, and `null` as `null`;
    /// the `String` overloads pass the value through, for filters with custom truth values.
    public enum Filter {
        /// The only instance.
        INSTANCE;

        private String str(Boolean b) {
            return b == null ? null : b.toString();
        }

        /// @param op the operator
        /// @param value the truth value, as the filter expects it
        /// @return the filter values
        public String[] of(Operator op, String value) {
            return new String[]{op.name(), value};
        }

        /// @param op the operator
        /// @param value the truth value, or `null` to compare with `NULL`
        /// @return the filter values
        public String[] of(Operator op, Boolean value) {
            return new String[]{op.name(), str(value)};
        }

        /// @param value the truth value, as the filter expects it
        /// @return the filter values
        public String[] eq(String value) {
            return new String[]{Operator.EQ.name(), value};
        }

        /// @param value the truth value, or `null` to match a `NULL` property
        /// @return the filter values
        public String[] eq(Boolean value) {
            return new String[]{Operator.EQ.name(), str(value)};
        }

        /// @param value the truth value, as the filter expects it
        /// @return the filter values
        public String[] neq(String value) {
            return new String[]{Operator.NEQ.name(), value};
        }

        /// @param value the truth value, or `null` to match a non-`NULL` property
        /// @return the filter values
        public String[] neq(Boolean value) {
            return new String[]{Operator.NEQ.name(), str(value)};
        }
    }
}
