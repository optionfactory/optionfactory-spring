package net.optionfactory.spring.data.jpa.filtering.filters;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.RepeatableTextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.TextCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

/// Whitelists a filter comparing a [String] property with a value, or two for [Operator#BETWEEN].
///
/// The filter values are a whitelisted [Operator], a whitelisted [CaseSensitivity] and the
/// operands. Only `EQ` and `NEQ` accept a `null` operand, to compare with `NULL`. The
/// `CONTAINS`, `STARTS_WITH` and `ENDS_WITH` operands are literal text: the `LIKE` wildcards `%` and
/// `_` they contain are escaped, so a client cannot inject a pattern.
///
/// With [CaseSensitivity#IGNORE_CASE] both sides are lower-cased (the operand with
/// `Locale.ROOT`), except for the `LIKE` operators, which use hibernate's `ilike` when the
/// criteria builder is hibernate's.
///
/// ```java
/// @Entity
/// @TextCompare(name = "byName", path = "name", operators = {Operator.EQ, Operator.CONTAINS})
/// public class Pet { ... }
///
/// FilterRequest.builder().text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "rex")).build();
/// ```
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(TextCompareFilter.class)
@Repeatable(RepeatableTextCompare.class)
public @interface TextCompare {

    /// The comparison requested by the client, as the first filter value. The ordering operators
    /// compare as the database collation does.
    public enum Operator {
        /// The property equals the value; a `null` value matches the `NULL` rows.
        EQ,
        /// The property differs from the value, `NULL` rows included; a `null` value matches the
        /// rows that are not `NULL`.
        NEQ,
        /// The property sorts before the value.
        LT,
        /// The property sorts after the value.
        GT,
        /// The property sorts before or equals the value.
        LTE,
        /// The property sorts after or equals the value.
        GTE,
        /// The property lies between two values, both included. The two values are swapped when
        /// they are out of order by Java's `String` ordering, which may differ from the collation.
        BETWEEN,
        /// The property contains the value.
        CONTAINS,
        /// The property starts with the value.
        STARTS_WITH,
        /// The property ends with the value.
        ENDS_WITH;
    }

    /// Whether the comparison tells upper and lower case apart, as the second filter value.
    public enum CaseSensitivity {
        /// Compares the text as is.
        CASE_SENSITIVE,
        /// Compares the lower-cased text.
        IGNORE_CASE;
    }

    /// @return the name the filter is whitelisted under, and requested by
    String name();

    /// @return the operators a client may request; an empty array is rejected when the repository
    /// is built
    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN, Operator.CONTAINS, Operator.STARTS_WITH, Operator.ENDS_WITH
    };

    /// @return the case sensitivities a client may request; an empty array is rejected when the
    /// repository is built
    CaseSensitivity[] caseSensitivity() default {
        CaseSensitivity.CASE_SENSITIVE, CaseSensitivity.IGNORE_CASE
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

    /// The container of repeated [TextCompare] annotations, used implicitly by the compiler when the
    /// annotation is repeated on an entity.
    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableTextCompare {

        /// @return the repeated annotations
        TextCompare[] value();
    }

    /// The filter whitelisted by [TextCompare].
    public static class TextCompareFilter implements TraversalFilter<String> {

        private final String name;
        private final EnumSet<Operator> operators;
        private final EnumSet<CaseSensitivity> caseSensitivity;
        private final Traversal traversal;

        /// @param annotation the whitelisting annotation
        /// @param entity the entity the annotation is on
        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
        /// when the path does not lead to a `String` property, misuses [Match], or whitelists no
        /// operator or no case sensitivity
        public TextCompareFilter(TextCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            Filters.ensureConfiguration(annotation.operators().length > 0, annotation.name(), entity, "operators must not be empty");
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            Filters.ensureConfiguration(annotation.caseSensitivity().length > 0, annotation.name(), entity, "caseSensitivity must not be empty");
            this.caseSensitivity = EnumSet.of(annotation.caseSensitivity()[0], annotation.caseSensitivity());
            this.traversal = Filters.traversal(entity, annotation.name(), annotation.path(), annotation.match());
            Filters.ensurePropertyOfAnyType(entity, annotation.name(), traversal, String.class);
        }

        /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when
        /// the number of values does not fit the operator, the operator or the case sensitivity is
        /// unknown or not whitelisted, or a `null` value is given to an operator other than `EQ`
        /// and `NEQ`
        @Override
        public Predicate condition(Root<?> root, Path<String> lpath, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 3 || values.length == 4, root, name, "expected operator,mode,value(s) got %s", Arrays.toString(values));
            final Operator operator = Filters.parseEnum(root, name, "operator", Operator.class, values[0]);
            Filters.ensure((values.length == 3 && operator != Operator.BETWEEN) || (values.length == 4 && operator == Operator.BETWEEN), root, name, "expected operator,mode,value(s) got %s", Arrays.toString(values));
            Filters.ensure(operators.contains(operator), root, name, "operator %s not whitelisted (%s)", operator, operators);
            final CaseSensitivity sensitivity = Filters.parseEnum(root, name, "mode", CaseSensitivity.class, values[1]);
            Filters.ensure(caseSensitivity.contains(sensitivity), root, name, "mode %s not whitelisted (%s)", sensitivity, caseSensitivity);
            final String value = values[2];
            final Expression<String> lhs = sensitivity == CaseSensitivity.CASE_SENSITIVE ? lpath : builder.lower(lpath);
            final String rhs = sensitivity == CaseSensitivity.CASE_SENSITIVE || value == null ? value : value.toLowerCase(Locale.ROOT);
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
                    final String value2 = values[3];
                    final String rhs2 = sensitivity == CaseSensitivity.CASE_SENSITIVE || value2 == null ? value2 : value2.toLowerCase(Locale.ROOT);
                    Filters.ensure(rhs != null, root, name, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, root, name, "value2 cannot be null for operator %s", operator);
                    final String[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new String[l]);
                    yield builder.and(builder.greaterThanOrEqualTo(lhs, sorted[0]), builder.lessThanOrEqualTo(lhs, sorted[1]));
                }
                case CONTAINS -> {
                    Filters.ensure(value != null, root, name, "value cannot be null for operator %s", operator);
                    yield like(builder, lpath, sensitivity, "%" + escapeForLike(value) + "%");
                }
                case STARTS_WITH -> {
                    Filters.ensure(value != null, root, name, "value cannot be null for operator %s", operator);
                    yield like(builder, lpath, sensitivity, escapeForLike(value) + "%");
                }
                case ENDS_WITH -> {
                    Filters.ensure(value != null, root, name, "value cannot be null for operator %s", operator);
                    yield like(builder, lpath, sensitivity, "%" + escapeForLike(value));
                }
                default ->
                    throw new IllegalStateException("unreachable");
            };
        }

        private static final char LIKE_ESCAPE_CHAR = '\\';
        private static final String LIKE_ESCAPE_STR = String.valueOf(LIKE_ESCAPE_CHAR);

        private static Predicate like(CriteriaBuilder builder, Path<String> path, CaseSensitivity sensitivity, String pattern) {
            if (sensitivity == CaseSensitivity.IGNORE_CASE && builder instanceof HibernateCriteriaBuilder hibernate) {
                return hibernate.ilike(path, pattern, LIKE_ESCAPE_CHAR);
            }
            final Expression<String> lhs = sensitivity == CaseSensitivity.CASE_SENSITIVE ? path : builder.lower(path);
            final String rhs = sensitivity == CaseSensitivity.CASE_SENSITIVE ? pattern : pattern.toLowerCase(Locale.ROOT);
            return builder.like(lhs, rhs, LIKE_ESCAPE_CHAR);
        }

        /// Escapes the `LIKE` wildcards `%` and `_`, and the escape character `\` itself, with a
        /// `\`: the result matches the input literally in a `LIKE` pattern declaring `\` as its
        /// escape character.
        ///
        /// @param input the literal text
        /// @return the escaped text
        public static String escapeForLike(String input) {
            return input
                    .replace(LIKE_ESCAPE_STR, LIKE_ESCAPE_STR + LIKE_ESCAPE_STR)
                    .replace("%", LIKE_ESCAPE_STR + "%")
                    .replace("_", LIKE_ESCAPE_STR + "_");
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

    /// Encodes the values of a [TextCompare] filter for a [net.optionfactory.spring.data.jpa.filtering.FilterRequest].
    ///
    /// The overloads without a [CaseSensitivity] request [CaseSensitivity#CASE_SENSITIVE].
    public enum Filter {
        /// The only instance.
        INSTANCE;

        /// @param op the operator
        /// @param sensitivity the case sensitivity
        /// @param values the operands
        /// @return the filter values
        public String[] of(Operator op, CaseSensitivity sensitivity, String... values) {
            return Stream.concat(
                    Stream.of(op.name(), sensitivity.name()),
                    Stream.of(values)
            ).toArray(i -> new String[i]);
        }

        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(String value) {
            return new String[]{Operator.EQ.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand, or `null` to match the `NULL` rows
        /// @return the filter values
        public String[] eq(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.EQ.name(), sensitivity.name(), value};
        }

        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(String value) {
            return new String[]{Operator.NEQ.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand, or `null` to match the rows that are not `NULL`
        /// @return the filter values
        public String[] neq(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.NEQ.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lt(String value) {
            return new String[]{Operator.LT.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] lt(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.LT.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gt(String value) {
            return new String[]{Operator.GT.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] gt(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.GT.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] lte(String value) {
            return new String[]{Operator.LTE.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] lte(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.LTE.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] gte(String value) {
            return new String[]{Operator.GTE.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] gte(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.GTE.name(), sensitivity.name(), value};
        }

        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(String value1, String value2) {
            return new String[]{Operator.BETWEEN.name(), CaseSensitivity.CASE_SENSITIVE.name(), value1, value2};
        }

        /// @param sensitivity the case sensitivity
        /// @param value1 one bound, included
        /// @param value2 the other bound, included
        /// @return the filter values
        public String[] between(CaseSensitivity sensitivity, String value1, String value2) {
            return new String[]{Operator.BETWEEN.name(), sensitivity.name(), value1, value2};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] contains(String value) {
            return new String[]{Operator.CONTAINS.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] contains(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.CONTAINS.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] startsWith(String value) {
            return new String[]{Operator.STARTS_WITH.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] startsWith(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.STARTS_WITH.name(), sensitivity.name(), value};
        }

        /// @param value the operand
        /// @return the filter values
        public String[] endsWith(String value) {
            return new String[]{Operator.ENDS_WITH.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        /// @param sensitivity the case sensitivity
        /// @param value the operand
        /// @return the filter values
        public String[] endsWith(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.ENDS_WITH.name(), sensitivity.name(), value};
        }
    }

}
