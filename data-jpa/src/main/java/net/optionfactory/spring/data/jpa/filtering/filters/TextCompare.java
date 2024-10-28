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
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.RepeatableTextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.TextCompareFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter;

/**
 * Compares a text property. The three arguments must be a whitelisted
 * {@link Operator}, a whitelisted {@link CaseSensitivity} and the comparison
 * value.
 */
@Documented
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
@WhitelistedFilter(TextCompareFilter.class)
@Repeatable(RepeatableTextCompare.class)
public @interface TextCompare {

    public enum Operator {
        EQ, NEQ, LT, GT, LTE, GTE, BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH;
    }

    public enum CaseSensitivity {
        CASE_SENSITIVE, IGNORE_CASE;
    }

    String name();

    QueryMode mode() default QueryMode.JOIN;

    Operator[] operators() default {
        Operator.EQ, Operator.NEQ, Operator.LT, Operator.GT, Operator.LTE, Operator.GTE, Operator.BETWEEN, Operator.CONTAINS, Operator.STARTS_WITH, Operator.ENDS_WITH
    };

    CaseSensitivity[] caseSensitivity() default {
        CaseSensitivity.CASE_SENSITIVE, CaseSensitivity.IGNORE_CASE
    };

    String path();

    @Documented
    @Target(value = ElementType.TYPE)
    @Retention(value = RetentionPolicy.RUNTIME)
    public static @interface RepeatableTextCompare {

        TextCompare[] value();
    }

    public static class TextCompareFilter implements TraversalFilter<String> {

        private final String name;
        private final QueryMode mode;
        private final EnumSet<Operator> operators;
        private final EnumSet<CaseSensitivity> caseSensitivity;
        private final Traversal traversal;

        public TextCompareFilter(TextCompare annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.mode = annotation.mode();
            this.operators = EnumSet.of(annotation.operators()[0], annotation.operators());
            this.caseSensitivity = EnumSet.of(annotation.caseSensitivity()[0], annotation.caseSensitivity());
            this.traversal = Filters.traversal(annotation, entity, annotation.path());
            Filters.ensurePropertyOfAnyType(annotation, entity, traversal, String.class);
        }

        @Override
        public Predicate condition(Root<?> root, Path<String> lpath, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 3 || values.length == 4, name, root, "expected operator,mode,value(s) got %s", Arrays.toString(values));
            final Operator operator = Operator.valueOf(values[0]);
            Filters.ensure((values.length == 3 && operator != Operator.BETWEEN) || (values.length == 4 && operator == Operator.BETWEEN), name, root, "expected operator,mode,value(s) got %s", Arrays.toString(values));
            Filters.ensure(operators.contains(operator), name, root, "operator %s not whitelisted (%s)", operator, operators);
            final CaseSensitivity mode = CaseSensitivity.valueOf(values[1]);
            Filters.ensure(caseSensitivity.contains(mode), name, root, "mode %s not whitelisted (%s)", mode, caseSensitivity);
            final String value = values[2];
            final Expression<String> lhs = mode == CaseSensitivity.CASE_SENSITIVE ? lpath : builder.lower(lpath);
            final String rhs = mode == CaseSensitivity.CASE_SENSITIVE || value == null ? value : value.toLowerCase();
            return switch (operator) {
                case EQ ->
                    rhs == null ? lhs.isNull() : builder.equal(lhs, rhs);
                case NEQ ->
                    rhs == null ? lhs.isNotNull() : builder.or(lhs.isNull(), builder.notEqual(lhs, rhs));
                case LT -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.lessThan(lhs, rhs);
                }
                case GT -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.greaterThan(lhs, rhs);
                }
                case LTE -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.lessThanOrEqualTo(lhs, rhs);
                }
                case GTE -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.greaterThanOrEqualTo(lhs, rhs);
                }
                case BETWEEN -> {
                    final String value2 = values[3];
                    final String rhs2 = mode == CaseSensitivity.CASE_SENSITIVE || value2 == null ? null : value2.toLowerCase();
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    Filters.ensure(rhs2 != null, name, root, "value2 cannot be null for operator %s", operator);
                    final String[] sorted = Stream.of(rhs, rhs2).sorted().toArray((l) -> new String[l]);
                    yield builder.and(builder.greaterThanOrEqualTo(lhs, sorted[0]), builder.lessThan(lhs, sorted[1]));
                }
                case CONTAINS -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.like(lhs, "%" + rhs + "%");
                }
                case STARTS_WITH -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.like(lhs, rhs + "%");
                }
                case ENDS_WITH -> {
                    Filters.ensure(rhs != null, name, root, "value cannot be null for operator %s", operator);
                    yield builder.like(lhs, "%" + rhs);
                }
                default ->
                    throw new IllegalStateException("unreachable");
            };
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public QueryMode mode() {
            return mode;
        }

        @Override
        public Traversal traversal() {
            return traversal;
        }
    }

    public static class Filter {

        public static String[] eq(String value) {
            return new String[]{Operator.EQ.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] eq(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.EQ.name(), sensitivity.name(), value};
        }

        public static String[] neq(String value) {
            return new String[]{Operator.NEQ.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] neq(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.NEQ.name(), sensitivity.name(), value};
        }

        public static String[] lt(String value) {
            return new String[]{Operator.LT.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] lt(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.LT.name(), sensitivity.name(), value};
        }

        public static String[] gt(String value) {
            return new String[]{Operator.GT.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] gt(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.GT.name(), sensitivity.name(), value};
        }

        public static String[] lte(String value) {
            return new String[]{Operator.LTE.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] lte(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.LTE.name(), sensitivity.name(), value};
        }

        public static String[] gte(String value) {
            return new String[]{Operator.GTE.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] gte(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.GTE.name(), sensitivity.name(), value};
        }

        public static String[] between(String value1, String value2) {
            return new String[]{Operator.BETWEEN.name(), CaseSensitivity.CASE_SENSITIVE.name(), value1, value2};
        }

        public static String[] between(CaseSensitivity sensitivity, String value1, String value2) {
            return new String[]{Operator.BETWEEN.name(), sensitivity.name(), value1, value2};
        }

        public static String[] contains(String value) {
            return new String[]{Operator.CONTAINS.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] contains(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.CONTAINS.name(), sensitivity.name(), value};
        }

        public static String[] startsWith(String value) {
            return new String[]{Operator.STARTS_WITH.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] startsWith(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.STARTS_WITH.name(), sensitivity.name(), value};
        }

        public static String[] endsWith(String value) {
            return new String[]{Operator.ENDS_WITH.name(), CaseSensitivity.CASE_SENSITIVE.name(), value};
        }

        public static String[] endsWith(CaseSensitivity sensitivity, String value) {
            return new String[]{Operator.ENDS_WITH.name(), sensitivity.name(), value};
        }
    }

}
