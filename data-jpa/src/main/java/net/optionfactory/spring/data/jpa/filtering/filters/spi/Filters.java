package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;
import net.optionfactory.spring.data.jpa.filtering.filters.Match;

/// Utility methods for evaluating entity graph traversals and validating filter runtime requests.
public interface Filters {

    record Step(String name, JoinType type) {

    }

    record Traversal(List<Step> steps, String leaf, Attribute<?, ?> attribute, String group, Match match) {

        public Traversal(List<Step> steps, String leaf, Attribute<?, ?> attribute, String group) {
            this(steps, leaf, attribute, group, Match.ANY);
        }

        @Override
        public String toString() {
            return String.format("%s.%s [Group: %s, Match: %s]",
                    steps.stream().map(Step::name).collect(Collectors.joining(".")),
                    leaf(), group != null ? group : "none", match);
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
    ///   constraints within a correlated `EXISTS` subquery node, negated when the filter's [Match] 
    ///   quantifier is [Match#NONE]. The collection itself is always entered with a [JoinType#INNER], 
    ///   an outer join inside the subquery being vacuously true, so a `joinType` override on a plural 
    ///   hop is ignored.
    /// - **Non-Relational Paths (Embeddables, Records, JSON columns):** Terminal or scalar non-association 
    ///   properties assign a `null` join type, allowing downstream components to safely navigate basic 
    ///   attributes via dot-notation without spawning redundant SQL `JOIN` declarations. However, if an 
    ///   embeddable property sits in the path leading to a relational association, it is automatically 
    ///   promoted to an SQM join using `JoinType.LEFT`.
    ///
    /// ### Subquery Context Management (`group` assignments)
    /// 
    /// Collection query contexts are managed dynamically via a three-tiered state check:
    /// 
    /// 1. **Context Initialization (`group == null`):** The first plural attribute encountered on 
    ///    a path starts a new query group, named after the current path segment string and the 
    ///    quantifier. Filters disagreeing on the quantifier describe different elements, so they land 
    ///    in different groups and get a subquery each.
    /// 2. **Context Folding (`reuse = true`):** Deep nested collection paths (e.g., `departments.employees`) 
    ///    naturally retain the active `group` identifier. This folds child conditions into the 
    ///    parent's existing `EXISTS` block, validating constraints collectively within the same table correlation.
    /// 3. **Context Isolation (`reuse = false`):** If a user explicitly registers a configuration override 
    ///    disabling reuse, the engine assigns a group token derived from the path and the filter name, unique 
    ///    to that filter and stable across restarts. This forces the query compiler to break away from parent 
    ///    folding and isolate that segment into its own distinct, standalone `EXISTS` block.
    /// 
    /// Evaluates the path with the default [Match#ANY] quantifier, for callers whose paths cannot 
    /// cross a collection (sorters, full-text search) or that want the existential reading.
    ///
    /// @param entity the JPA root metamodel descriptor
    /// @param filterName the alphanumeric identifier of the filter being evaluated
    /// @param path the raw dot-separated target path (e.g., `"departments.employees.name"`)
    /// @return a fully compiled graph traversal specification
    static Traversal traversal(EntityType<?> entity, String filterName, String path) {
        return traversal(entity, filterName, path, Match.ANY);
    }

    /// @param entity the JPA root metamodel descriptor
    /// @param filterName the alphanumeric identifier of the filter being evaluated
    /// @param path the raw dot-separated target path (e.g., `"departments.employees.name"`)
    /// @param quantifier what the filter asks of the elements of the collection its path crosses; 
    ///        [Match#NONE] requires such a collection, and is rejected on a path without one
    /// @return a fully compiled graph traversal specification
    static Traversal traversal(EntityType<?> entity, String filterName, String path, Match quantifier) {
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
            currentPath.append(currentPath.isEmpty() ? "" : ".").append(attributeName);
            final String pathString = currentPath.toString();

            if (currentAttribute != null && (currentAttribute.isAssociation() || currentAttribute.isCollection())) {
                final FilterTraversal override = overrides.get(pathString);
                JoinType resolvedJoinType = override != null ? override.joinType() : JoinType.LEFT;
                final boolean reuse = override != null ? override.reuse() : true;

                if (currentAttribute instanceof PluralAttribute) {
                    if (group == null) {
                        // first plural attribute encountered: we start a new subquery group.                        
                        group = group(pathString, filterName, reuse, quantifier);
                    } else if (!reuse) {
                        // already inside a subquery, but user explicitly requested to break out.
                        group = group(pathString, filterName, false, quantifier);
                    }
                    // if group is not null and reuse is true, we do nothing and inherit the parent's subquery group.
                    // a collection is always joined inside its EXISTS: an outer join there would yield a row for
                    // every parent, making the subquery vacuously true. Match decides who is kept, not the join type.
                    resolvedJoinType = JoinType.INNER;
                }
                // Embedded hops leading to an association must be promoted to JoinType.LEFT.
                // In SQL, @Embedded properties share the parent table, so using JoinType.LEFT 
                // for the embeddable hop emits zero extra SQL joins while preventing join-type 
                // collisions when sibling associations within the same embeddable use different JoinTypes.
                for (int j = 0; j != pathList.size(); j++) {
                    if (pathList.get(j).type() == null) {
                        pathList.set(j, new Step(pathList.get(j).name(), JoinType.LEFT));
                    }
                }
                pathList.add(new Step(attributeName, resolvedJoinType));
            } else {
                // fallback for embeddables, records, or JSON properties
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
        // a quantifier with nothing to quantify over reads as a condition it does not apply: rejected
        // here rather than ignored, since `match = NONE` on a scalar path states the opposite of what
        // the filter would then do
        ensureConfiguration(quantifier == Match.ANY || group != null, filterName, entity, "match %s requires a path crossing a collection, got %s", quantifier, path);
        return new Traversal(pathList, leaf, currentAttribute, group, quantifier);
    }

    /// Names the subquery a filter's conditions are folded into.
    ///
    /// The quantifier is part of the identity: a group describes *one element*, and asking that such an
    /// element exist is a different question from asking that none does, so filters disagreeing on it
    /// cannot share a subquery and simply get one each.
    ///
    /// With `reuse = false` the filter name isolates the group from every other filter. The filter name
    /// alone is enough, as filters are whitelisted by name, and deriving the token rather than minting a
    /// random one keeps the emitted SQL identical across restarts, so its cached plan stays usable.
    private static String group(String pathString, String filterName, boolean reuse, Match quantifier) {
        return reuse
                ? String.format("%s#%s", pathString, quantifier)
                : String.format("%s!%s#%s", pathString, filterName, quantifier);
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

    static void ensureConfiguration(boolean test, String filterName, EntityType<?> entity, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterConfiguration(filterName, entity, String.format(format, values));
        }
    }

    private static From<?, ?> step(Root<?> root, String filterName, From<?, ?> from, String attribute, JoinType jt) {
        for (Join<?, ?> join : from.getJoins()) {
            if (!join.getAttribute().getName().equals(attribute)) {
                continue;
            }
            ensure(join.getJoinType() == jt, root, filterName, "inconsistent join configuration requested on %s: already joined as %s, requested as %s", attribute, join.getJoinType(), jt);
            return join;
        }
        return from.join(attribute, jt);
    }

    @SuppressWarnings("unchecked")
    static <T> Path<T> path(Root<?> root, String filterName, Traversal traversal) {
        Path<?> current = root;

        for (Step step : traversal.steps()) {
            if (step.type() != null && current instanceof From<?, ?> from) {
                current = step(root, filterName, from, step.name(), step.type());
            } else {
                current = current.get(step.name());
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
