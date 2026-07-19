package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterGroup;

/**
 * Utility methods for evaluating graph traversals and validating filter
 * configurations.
 */
public interface Filters {

    /**
     * Represents a single relational hop along a nested property path graph.
     *
     * @param name the name of the managed attribute/relationship being
     * traversed
     * @param type the {@link JoinType} applied if this step is executed as a
     * SQL join, or null if it is navigated as a basic field or embedded path
     * component
     * @param reuse flags whether this join can merge with an existing join node
     * on the same context
     */
    record Step(String name, JoinType type, boolean reuse) {

    }

    /**
     * A fully resolved path blueprint used to navigate the JPA entity graph
     * from a root descriptor down to a specific filtering destination leaf.
     *
     * @param joins the ordered sequence of relational steps required to
     * navigate to the context boundary
     * @param leaf the final property field name where the comparison takes
     * place
     * @param attribute the raw JPA Metamodel descriptor for the target leaf
     * property
     * @param group the correlation context token used exclusively by the query
     * planner adapter
     */
    record Traversal(List<Step> joins, String leaf, Attribute<?, ?> attribute, String group) {

        @Override
        public String toString() {
            return String.format("%s.%s [Group: %s]",
                    joins.stream().map(Step::name).collect(java.util.stream.Collectors.joining(".")),
                    leaf(), group != null ? group : "none");
        }
    }

    static Traversal traversal(Annotation annotation, EntityType<?> entity, String path) {
        if (path == null || path.isEmpty()) {
            return new Traversal(List.of(), "", null, null);
        }

        String group = null;
        JoinType joinType = JoinType.INNER;
        boolean reuse = true;

        if (path.contains(".")) {
            int maxMatchLength = -1;
            Class<?> javaType = entity.getJavaType();

            // subselect groups (Enforcing trailing dot normalization)
            for (var sub : javaType.getAnnotationsByType(FilterGroup.Subselect.class)) {
                String matchPrefix = sub.value().endsWith(".") ? sub.value() : sub.value() + ".";
                if (path.startsWith(matchPrefix) && matchPrefix.length() > maxMatchLength) {
                    maxMatchLength = matchPrefix.length();
                    group = sub.reuse() ? matchPrefix : UUID.randomUUID().toString();
                    joinType = JoinType.INNER; // Subqueries require joins to navigate plural relationships
                    reuse = true;
                }
            }

            // 2. Resolve Join Groups (Longest Match Wins)
            for (var join : javaType.getAnnotationsByType(FilterGroup.Join.class)) {
                String matchPrefix = join.value().endsWith(".") ? join.value() : join.value() + ".";
                if (path.startsWith(matchPrefix) && matchPrefix.length() > maxMatchLength) {
                    maxMatchLength = matchPrefix.length();
                    group = null;
                    joinType = join.type();
                    reuse = join.reuse();
                }
            }

            if (maxMatchLength == -1) {
                throw new InvalidFilterConfiguration(annotation, entity,
                        String.format("Path '%s' crosses a relationship boundary but does not match any declared @FilterGroup prefix.", path)
                );
            }
        }

        // 3. Map the Graph Hops using JPA Metamodel Inspection
        ManagedType<?> currentType = entity;
        Attribute<?, ?> currentAttribute = null;
        final List<Step> pathList = new ArrayList<>();
        String[] parts = path.split("\\.");

        for (int i = 0; i < parts.length; i++) {
            String attributeName = parts[i];
            if (currentType != null) {
                currentAttribute = currentType.getAttribute(attributeName);
            }

            boolean isLast = (i == parts.length - 1);
            if (!isLast) {
                if (currentAttribute != null && (currentAttribute.isAssociation() || currentAttribute.isCollection())) {
                    pathList.add(new Step(attributeName, joinType, reuse));
                } else {
                    // Safe automated fallback for Embeddables, Records, or JSON properties
                    pathList.add(new Step(attributeName, null, false));
                }

                if (currentAttribute instanceof SingularAttribute sa && sa.getType() instanceof ManagedType mt) {
                    currentType = mt;
                } else if (currentAttribute instanceof PluralAttribute pa && pa.getElementType() instanceof ManagedType mt) {
                    currentType = mt;
                } else {
                    currentType = null;
                }
            }
        }

        String leaf = parts.length > 0 ? parts[parts.length - 1] : "";
        return new Traversal(pathList, leaf, currentAttribute, group);
    }

    static Class<?> ensurePropertyOfAnyType(Annotation annotation, EntityType<?> entity, Traversal traversal, Class<?>... types) {
        final Class<?> javaType = traversal.attribute() == null ? entity.getJavaType() : traversal.attribute().getJavaType();
        return Stream.of(types)
                .filter(type -> type.isAssignableFrom(javaType))
                .findFirst()
                .orElseThrow(() -> new InvalidFilterConfiguration(annotation, entity, String.format("expected traversal %s to be of type %s, got %s", traversal.leaf(), List.of(types), javaType.getSimpleName())));
    }

    static void ensure(boolean test, String filterName, Root<?> root, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(filterName, root, String.format(format, values));
        }
    }

    private static From<?, ?> join(String filterName, Root<?> root, From<?, ?> from, String attribute, JoinType jt, boolean reuse) {
        if (!reuse) {
            return from.join(attribute, jt);
        }
        return from.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals(attribute))
                .peek(j -> ensure(j.getJoinType() == jt, filterName, root, "Inconsistent join configuration requested: %s", attribute))
                .findFirst()
                .orElseGet(() -> from.join(attribute, jt));
    }

    @SuppressWarnings("unchecked")
    static <T> Path<T> path(String filterName, Root<?> root, Traversal traversal) {
        Path<?> current = root;
        for (Step step : traversal.joins()) {
            if (step.type() == null) {
                // navigate embeddables, records, and JSON fields
                current = current.get(step.name());
            } else {
                // cross an entity relationship boundary using a SQL Join
                current = join(filterName, root, (From<?, ?>) current, step.name(), step.type(), step.reuse());
            }
        }
        if (traversal.leaf() == null || traversal.leaf().isEmpty()) {
            return (Path<T>) current;
        }
        return (Path<T>) current.get(traversal.leaf());
    }

    static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String filterName, Root<?> root, String fieldDescription) {
        if (value == null) {
            throw new InvalidFilterRequest(filterName, root, String.format("%s parameter cannot be null", fieldDescription));
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new InvalidFilterRequest(filterName, root, String.format("Unknown value '%s' for %s", value, fieldDescription));
        }
    }
}
