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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;

/// Utility methods for evaluating entity graph traversals and validating filter runtime requests.
public interface Filters {

    record Step(String name, JoinType type) {

    }

    record Traversal(List<Step> joins, String leaf, Attribute<?, ?> attribute, String group) {

        @Override
        public String toString() {
            return String.format("%s.%s [Group: %s]",
                    joins.stream().map(Step::name).collect(Collectors.joining(".")),
                    leaf(), group != null ? group : "none");
        }
    }

    /// Evaluates a dot-notated property path relative to a root JPA Entity and builds a fully 
    /// resolved execution map.
    /// 
    /// ### Metamodel Parsing & Nuances
    /// 
    /// This method evaluates paths segment-by-segment against the JPA Metamodel to auto-deduce 
    /// query execution strategies:
    /// 
    /// - **Singular Associations (`@ManyToOne`, `@OneToOne`):** Paths crossing singular relations 
    ///   default to `JoinType.LEFT` to prevent data truncation during negation or null-checking operations.
    /// - **Plural Associations (`@OneToMany`, `@ManyToMany`):** Paths crossing collection boundaries 
    ///   automatically assign a `group` token, flagging the query planner adapter to compile these 
    ///   constraints within a correlated `EXISTS` subquery node.
    /// - **Non-Relational Paths (Embeddables, Records, JSON columns):** Non-association properties 
    ///   assign a `null` join type. This lets downstream components safely navigate basic attributes 
    ///   using structural dot-notation without spawning redundant SQL `JOIN` declarations.
    /// 
    /// ### Subquery Context Management (`group` assignments)
    /// 
    /// Collection query contexts are managed dynamically via a three-tiered state check:
    /// 
    /// 1. **Context Initialization (`group == null`):** The first plural attribute encountered on 
    ///    a path starts a new query group named after the current path segment string.
    /// 2. **Context Folding (`reuse = true`):** Deep nested collection paths (e.g., `departments.employees`) 
    ///    naturally retain the active `group` identifier. This folds child conditions into the 
    ///    parent's existing `EXISTS` block, validating constraints collectively within the same table correlation.
    /// 3. **Context Isolation (`reuse = false`):** If a user explicitly registers a configuration override 
    ///    disabling reuse, the engine assigns an isolated random `UUID` string. This forces the query 
    ///    compiler to break away from parent folding and isolate that segment into its own distinct, standalone 
    ///    `EXISTS` block.
    /// 
    /// @param entity the JPA root metamodel descriptor
    /// @param filterName the alphanumeric identifier of the filter being evaluated
    /// @param path the raw dot-separated target path (e.g., `"departments.employees.name"`)
    /// @return a fully compiled graph traversal specification
    static Traversal traversal(EntityType<?> entity, String filterName, String path) {
        if (path == null || path.isEmpty()) {
            return new Traversal(List.of(), "", null, null);
        }

        // overrides by @FilterTraversal annotation
        final Map<String, FilterTraversal> overrides = Stream.of(entity.getJavaType().getAnnotationsByType(FilterTraversal.class))
                .collect(Collectors.toMap(FilterTraversal::path, ft -> ft));

        ManagedType<?> currentType = entity;
        Attribute<?, ?> currentAttribute = null;
        final List<Step> pathList = new ArrayList<>();
        final var parts = path.split("\\.");
        String group = null;
        final var currentPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            final var attributeName = parts[i];
            if (currentType != null) {
                currentAttribute = currentType.getAttribute(attributeName);
            }

            final var isLast = (i == parts.length - 1);
            if (isLast) {
                break;
            }

            currentPath.append(currentPath.length() > 0 ? "." : "").append(attributeName);
            final String pathString = currentPath.toString();

            if (currentAttribute != null && (currentAttribute.isAssociation() || currentAttribute.isCollection())) {
                FilterTraversal override = overrides.get(pathString);
                JoinType resolvedJoinType = override != null ? override.joinType() : JoinType.LEFT;
                boolean reuse = override != null ? override.reuse() : true;

                if (currentAttribute instanceof PluralAttribute) {
                    if (group == null) {
                        // first plural attribute encountered: we start a new subquery group.
                        group = reuse ? pathString : UUID.randomUUID().toString();
                    } else if (!reuse) {
                        // already inside a subquery, but user explicitly requested to break out.
                        group = UUID.randomUUID().toString();
                    }
                    // if group is not null and reuse is true, we do nothing and inherit the parent's subquery group.
                }
                pathList.add(new Step(attributeName, resolvedJoinType));
            } else {
                // fallback for embeddables, records, or JSON properties]
                pathList.add(new Step(attributeName, null));
            }

            if (currentAttribute instanceof SingularAttribute sa && sa.getType() instanceof ManagedType mt) {
                currentType = mt;
            } else if (currentAttribute instanceof PluralAttribute pa && pa.getElementType() instanceof ManagedType mt) {
                currentType = mt;
            } else {
                currentType = null;
            }
        }

        final var leaf = parts.length > 0 ? parts[parts.length - 1] : "";
        return new Traversal(pathList, leaf, currentAttribute, group);
    }

    static Class<?> ensurePropertyOfAnyType(EntityType<?> entity, String filterName, Traversal traversal, Class<?>... types) {
        final Class<?> javaType = traversal.attribute() == null ? entity.getJavaType() : traversal.attribute().getJavaType();
        return Stream.of(types)
                .filter(type -> type.isAssignableFrom(javaType))
                .findFirst()
                .orElseThrow(() -> new InvalidFilterConfiguration(filterName, entity, String.format("expected traversal %s to be of type %s, got %s", traversal.leaf(), List.of(types), javaType.getSimpleName())));
    }

    static void ensure(boolean test, Root<?> root, String filterName, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(filterName, root, String.format(format, values));
        }
    }

    private static From<?, ?> join(Root<?> root, String filterName, From<?, ?> from, String attribute, JoinType jt) {
        return from.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals(attribute))
                .peek(j -> ensure(j.getJoinType() == jt, root, filterName, "Inconsistent join configuration requested: %s", attribute))
                .findFirst()
                .orElseGet(() -> from.join(attribute, jt));
    }

    @SuppressWarnings("unchecked")
    static <T> Path<T> path(Root<?> root, String filterName, Traversal traversal) {
        Path<?> current = root;
        for (Step step : traversal.joins()) {
            if (step.type() == null) {
                current = current.get(step.name());
            } else {
                current = join(root, filterName, (From<?, ?>) current, step.name(), step.type());
            }
        }
        if (traversal.leaf() == null || traversal.leaf().isEmpty()) {
            return (Path<T>) current;
        }
        return (Path<T>) current.get(traversal.leaf());
    }

    static <E extends Enum<E>> E parseEnum(Root<?> root, String filterName, String fieldDescription, Class<E> enumClass, String value) {
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
