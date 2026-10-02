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

/// Utility methods for evaluating entity graph traversals and validating filter runtime requests,
/// for the built-in filters and for custom ones alike.
///
/// The `ensure*` methods separate the two failure modes a filter has: an
/// [InvalidFilterConfiguration] is a mistake in the whitelisting, meant to fail the startup, while
/// an [InvalidFilterRequest] is a mistake in what the client sent, meant to be reported back to it.
public interface Filters {

    /// One hop of a [Traversal], from the root or the previous hop to an intermediate attribute.
    ///
    /// @param name the attribute name
    /// @param type the join type the hop is navigated with, or `null` for an attribute navigated
    /// with a plain `get`, emitting no join (an embeddable, a json property)
    record Step(String name, JoinType type) {

    }

    /// A property path resolved against the entity metamodel, telling the repository how to reach
    /// the filtered property and whether to evaluate its condition in a subquery.
    ///
    /// @param steps the hops leading to the leaf, in order
    /// @param leaf the name of the final attribute, read with a plain `get`; empty for the root
    /// itself
    /// @param attribute the metamodel attribute of the leaf, or `null` for the root itself
    /// @param group the subquery the condition is folded into, `null` when the path crosses no
    /// collection; filters with the same group share one `EXISTS`
    /// @param match the quantifier of the subquery, [Match#ANY] or [Match#NONE]
    record Traversal(List<Step> steps, String leaf, Attribute<?, ?> attribute, String group, Match match) {

        /// A traversal quantified by [Match#ANY].
        ///
        /// @param steps the hops leading to the leaf, in order
        /// @param leaf the name of the final attribute
        /// @param attribute the metamodel attribute of the leaf
        /// @param group the subquery the condition is folded into, or `null`
        public Traversal(List<Step> steps, String leaf, Attribute<?, ?> attribute, String group) {
            this(steps, leaf, attribute, group, Match.ANY);
        }

        /// @return a description of the path, its group and its quantifier, for diagnostics
        @Override
        public String toString() {
            return String.format("%s.%s [Group: %s, Match: %s]",
                    steps.stream().map(Step::name).collect(Collectors.joining(".")),
                    leaf(), group != null ? group : "none", match);
        }
    }

    /// Evaluates the path with no quantifier stated, for a custom filter that has none of its own: the
    /// path is accepted when it crosses no collection, and rejected when it crosses one, since the
    /// filter would then have to say whether some element or no element must satisfy it — build such
    /// a traversal with [#traversal(EntityType, String, String, Match)] instead.
    ///
    /// @param entity the JPA root metamodel descriptor
    /// @param filterName the name of the filter being evaluated, used in error messages
    /// @param path the raw dot-separated target path (e.g., `"address.state"`)
    /// @return the resolved traversal
    /// @throws InvalidFilterConfiguration when the path crosses a collection
    /// @throws IllegalArgumentException when a path segment names no attribute
    static Traversal traversal(EntityType<?> entity, String filterName, String path) {
        return traversal(entity, filterName, path, Match.UNSTATED);
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
    ///   promoted to an SQM join using `JoinType.LEFT`: an embeddable shares its owner's table, so
    ///   the join emits no SQL of its own, while it keeps sibling associations within the same
    ///   embeddable from colliding when they use different join types.
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
    /// A collection is always joined `INNER` inside its `EXISTS`, whatever the override says: an
    /// outer join there would yield a row for every parent, making the subquery vacuously true. The
    /// quantifier decides which parents are kept, not the join type. A [Match#NONE] quantifier on a
    /// path crossing no collection is rejected rather than ignored, since it would state the
    /// opposite of what the filter then does.
    ///
    /// An empty or `null` path resolves to the root itself.
    ///
    /// @param entity the JPA root metamodel descriptor
    /// @param filterName the name of the filter being evaluated, used in error messages and in the
    /// group of an isolated subquery
    /// @param path the raw dot-separated target path (e.g., `"departments.employees.name"`)
    /// @param quantifier what the filter asks of the elements of the collection its path crosses:
    ///        a path crossing one requires [Match#ANY] or [Match#NONE], and rejects [Match#UNSTATED];
    ///        a path crossing none rejects [Match#NONE] and reads the other two alike
    /// @return the resolved traversal, whose match is [Match#ANY] when the quantifier is unstated
    /// @throws InvalidFilterConfiguration when the quantifier does not fit the path
    /// @throws IllegalArgumentException when a path segment names no attribute
    static Traversal traversal(EntityType<?> entity, String filterName, String path, Match quantifier) {
        if (path == null || path.isEmpty()) {
            return new Traversal(List.of(), "", null, null);
        }

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
                        group = group(pathString, filterName, reuse, quantifier);
                    } else if (!reuse) {
                        group = group(pathString, filterName, false, quantifier);
                    }
                    resolvedJoinType = JoinType.INNER;
                }
                for (int j = 0; j != pathList.size(); j++) {
                    if (pathList.get(j).type() == null) {
                        pathList.set(j, new Step(pathList.get(j).name(), JoinType.LEFT));
                    }
                }
                pathList.add(new Step(attributeName, resolvedJoinType));
            } else {
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
        ensureConfiguration(quantifier != Match.NONE || group != null, filterName, entity, "match %s requires a path crossing a collection, got %s", quantifier, path);
        ensureConfiguration(quantifier != Match.UNSTATED || group == null, filterName, entity, "path %s crosses a collection: its quantifier must be stated, Match.ANY (some element satisfies the filter) or Match.NONE (no element does)", path);
        return new Traversal(pathList, leaf, currentAttribute, group, quantifier == Match.UNSTATED ? Match.ANY : quantifier);
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

    /// Checks, when the filter is created, that the traversal leads to a property of one of the
    /// given types (the entity itself for an empty path).
    ///
    /// @param entity the entity the filter is on
    /// @param filterName the filter name, for the error message
    /// @param traversal the resolved path
    /// @param types the accepted types, in order of preference; a primitive type only accepts that
    /// primitive, not its boxed counterpart
    /// @return the first accepted type the property is assignable to
    /// @throws InvalidFilterConfiguration when the property is of none of the types
    static Class<?> ensurePropertyOfAnyType(EntityType<?> entity, String filterName, Traversal traversal, Class<?>... types) {
        final Class<?> javaType = traversal.attribute() == null ? entity.getJavaType() : traversal.attribute().getJavaType();
        return Stream.of(types)
                .filter(type -> type.isAssignableFrom(javaType))
                .findFirst()
                .orElseThrow(() -> new InvalidFilterConfiguration(filterName, entity, String.format("expected traversal %s to be of type %s, got %s", traversal.leaf(), List.of(types), javaType.getSimpleName())));
    }

    /// Checks a precondition on the values a client sent.
    ///
    /// @param test the precondition
    /// @param root the query root, naming the entity in the message
    /// @param filterName the filter name
    /// @param format the reason, as a `String.format` pattern; it is safe to show to the client
    /// @param values the pattern arguments
    /// @throws InvalidFilterRequest when the precondition does not hold
    static void ensure(boolean test, Root<?> root, String filterName, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(filterName, root, String.format(format, values));
        }
    }

    /// Checks a precondition on the whitelisting of a filter, when the filter is created.
    ///
    /// @param test the precondition
    /// @param filterName the filter name
    /// @param entity the entity the filter is on
    /// @param format the reason, as a `String.format` pattern
    /// @param values the pattern arguments
    /// @throws InvalidFilterConfiguration when the precondition does not hold
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
            ensureConfiguration(join.getJoinType() == jt, filterName, root.getModel(), "inconsistent join configuration requested on %s: already joined as %s, requested as %s", attribute, join.getJoinType(), jt);
            return join;
        }
        return from.join(attribute, jt);
    }

    /// Navigates a traversal from a root, joining the hops that have a join type.
    ///
    /// A join of the same attribute already present on the parent is reused, so that filters and
    /// sorters sharing a path share its join too.
    ///
    /// @param <T> the type of the leaf property
    /// @param root the root to start from: the query root, or the correlated root of a subquery
    /// @param filterName the filter name, for the error message
    /// @param traversal the resolved path
    /// @return the leaf property, or the root itself for an empty path
    /// @throws InvalidFilterConfiguration when a reused join was opened with a different join type,
    /// as when two filters disagree through their `@FilterTraversal` overrides
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

    /// Parses an enum constant a client sent, by its exact name.
    ///
    /// @param <E> the enum type
    /// @param root the query root, naming the entity in the message
    /// @param filterName the filter name
    /// @param fieldDescription what the value is (`operator`, `mode`, ...), for the message
    /// @param enumClass the enum type
    /// @param value the constant name
    /// @return the constant
    /// @throws InvalidFilterRequest when the value is `null` or names no constant
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
