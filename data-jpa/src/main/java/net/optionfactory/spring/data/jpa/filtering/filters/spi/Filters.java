package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.QueryMode;

/**
 * Utility methods for defining filters.
 */
public interface Filters {

    public static final Pattern ATTRIBUTE_TRAVERSAL_PATTERN = Pattern.compile(String.format(
            "(?:<(?<type>%s)>)?(?<attribute>.+)", Stream.of(TraversalType.values()).map(TraversalType::name).collect(Collectors.joining("|"))
    ));

    public static Traversal traversal(Annotation annotation, EntityType<?> entity, String pathTraversalSpec) {
        ManagedType<?> currentType = entity;
        Attribute<?, ?> currentAttribute = null;
        final var path = new ArrayList<AttributeTraversal>();

        if (pathTraversalSpec == null || pathTraversalSpec.isEmpty()) {
            return new Traversal(path, currentAttribute);
        }

        for (String attributeTraversalSpec : pathTraversalSpec.split("\\.")) {
            final Matcher m = ATTRIBUTE_TRAVERSAL_PATTERN.matcher(attributeTraversalSpec);
            ensureConf(m.matches(), annotation, entity, "invalid attribute traversal spec: %s", attributeTraversalSpec);
            final String type = m.group("type");
            final String attributeName = m.group("attribute");
            ensureConf(currentType != null, annotation, entity, "referenced an attribute %s in spec %s owned by a non-managed type", attributeName, pathTraversalSpec);
            try {
                currentAttribute = currentType.getAttribute(attributeName);
            } catch (IllegalArgumentException exception) {
                throw new InvalidFilterConfiguration(annotation, entity, String.format("referenced a non-existent property %s.%s in spec %s", currentType.getJavaType().getSimpleName(), attributeName, pathTraversalSpec));
            }
            if (currentAttribute instanceof SingularAttribute sa) {
                final AttributeTraversal candidate = new AttributeTraversal(attributeName, Optional.ofNullable(type).map(TraversalType::valueOf).orElse(TraversalType.GET));
                path.add(candidate);
                final Type targetType = sa.getType();
                if (targetType instanceof ManagedType) {
                    currentType = (ManagedType<?>) targetType;
                } else {
                    currentType = null;
                }
                continue;
            }
            if (currentAttribute instanceof PluralAttribute pa) {
                final AttributeTraversal candidate = new AttributeTraversal(attributeName, Optional.ofNullable(type).map(TraversalType::valueOf).orElse(TraversalType.INNER_JOIN_REUSE));
                ensureConf(TraversalType.GET != candidate.type, annotation, entity, "used an invalid traversal type '%s' for plural attribute '%s' in spec: '%s'", candidate.type, candidate.name, pathTraversalSpec);
                path.add(candidate);
                final Type targetType = pa.getElementType();
                if (targetType instanceof ManagedType) {
                    currentType = (ManagedType<?>) targetType;
                } else {
                    currentType = null;
                }
                continue;
            }
            ensureConf(false, annotation, entity, "referenced an attribute %s with spec %s which is neither a SingularAttribute nor a PluralAttribute", attributeName, pathTraversalSpec);
        }
        return new Traversal(path, currentAttribute);
    }

    public static Class<?> ensurePropertyOfAnyType(Annotation annotation, EntityType<?> entity, Traversal traversal, Class<?>... types) {
        final Class<?> javaType = traversal.attribute == null ? entity.getJavaType() : traversal.attribute.getJavaType();
        return Stream.of(types)
                .filter(type -> type.isAssignableFrom(javaType))
                .findFirst()
                .orElseThrow(() -> new InvalidFilterConfiguration(annotation, entity, String.format("expected traversal %s to be of type %s, got %s", traversal.path, List.of(types), traversal.attribute.getJavaType().getSimpleName())));
    }

    private static void ensureConf(boolean test, Annotation annotation, EntityType<?> entity, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterConfiguration(annotation, entity, String.format(format, values));
        }
    }

    public static void ensure(boolean test, String filterName, Root<?> root, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(filterName, root, String.format(format, values));
        }
    }

    private static Path<?> join(String filterName, Root<?> root, Path<?> path, String attribute, JoinType jt, boolean reuse) {
        if (!reuse) {
            return ((From<?, ?>) path).join(attribute, jt);
        }
        final var from = ((From<?, ?>) path);
        return from
                .getJoins()
                .stream()
                .filter(j -> j.getAttribute().getName().equals(attribute))
                .peek(j -> ensure(j.getJoinType() == jt, filterName, root, "Inconsistent join in filter: Requested join:(%s,%s) Former join:(%s,%s)", attribute, jt, j.getAttribute().getName(), j.getJoinType()))
                .findFirst()
                .orElseGet(() -> from.join(attribute, jt));
    }

    public static class ModalQuery {

        public final CriteriaBuilder builder;
        public final Root<?> root;
        public final Root<?> conditionRoot;
        public final Subquery<Integer> sq;

        public ModalQuery(CriteriaBuilder builder, Root<?> root, Root<?> conditionRoot, Subquery<Integer> sq) {
            this.builder = builder;
            this.root = root;
            this.conditionRoot = conditionRoot;
            this.sq = sq;
        }
    }

    public static ModalQuery prepare(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, QueryMode mode) {
        if (mode == QueryMode.JOIN) {
            return new ModalQuery(builder, root, root, null);
        }
        final Subquery<Integer> sq = query.subquery(Integer.class);
        final Root<?> conditionRoot = sq.from(root.getJavaType());
        return new ModalQuery(builder, root, conditionRoot, sq);
    }

    public static Predicate apply(ModalQuery mq, Predicate condition) {
        final var builder = mq.builder;
        final var subquery = mq.sq;
        if (subquery == null) {
            return condition;
        }
        return builder.exists(
                subquery.select(builder.literal(1))
                        .where(builder.and(
                                builder.equal(mq.conditionRoot, mq.root),
                                condition
                        ))
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> Path<T> path(String filterName, Root<?> root, Traversal traversal) {
        Path<?> path = root;
        for (AttributeTraversal part : traversal.path) {
            switch (part.type) {
                case GET -> path = path.get(part.name);
                case INNER_JOIN -> path = join(filterName, root, path, part.name, JoinType.INNER, false);
                case INNER_JOIN_REUSE -> path = join(filterName, root, path, part.name, JoinType.INNER, true);
                case LEFT_JOIN -> path = join(filterName, root, path, part.name, JoinType.LEFT, false);
                case LEFT_JOIN_REUSE -> path = join(filterName, root, path, part.name, JoinType.LEFT, true);
                default -> ensure(false, filterName, root, "Unsupported TraversalType: %s for %s in traversal %s", part.type, part.name, traversal);
            }
        }
        return (Path<T>) path;
    }

    public enum TraversalType {
        LEFT_JOIN,
        INNER_JOIN,
        INNER_JOIN_REUSE,
        LEFT_JOIN_REUSE,
        GET;
    }

    public static class Traversal {

        public final List<AttributeTraversal> path;
        public final Attribute<?, ?> attribute;

        public Traversal(List<AttributeTraversal> path, Attribute<?, ?> attribute) {
            this.path = path;
            this.attribute = attribute;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", path, attribute.getJavaType());
        }

    }

    public static class AttributeTraversal {

        public final String name;
        public final TraversalType type;

        public AttributeTraversal(String name, TraversalType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", name, type);
        }

    }

}
