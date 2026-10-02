package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

/// A [Filter] whose condition applies to the property at the end of a path, resolved once into a
/// [Traversal] when the filter is created.
///
/// Implementing it, rather than [Filter] directly, lets the repository navigate the path for the
/// filter: associations are joined (reusing the joins other filters and sorters opened on the same
/// path), and a path crossing a collection is evaluated inside a correlated `EXISTS` subquery,
/// negated when the traversal's quantifier is
/// [net.optionfactory.spring.data.jpa.filtering.filters.Match#NONE], and shared with the other
/// filters reaching the same collection with the same quantifier. Every built-in path-based filter
/// is one.
///
/// ```java
/// public class HasTag implements TraversalFilter<String> {
///
///     private final String name;
///     private final Traversal traversal;
///
///     public HasTag(Filterable annotation, EntityType<?> entity) {
///         this.name = annotation.name();
///         this.traversal = Filters.traversal(entity, name, "tags.label", Match.ANY);
///     }
///
///     public Predicate condition(Root<?> root, Path<String> path, CriteriaBuilder builder, String[] values) {
///         Filters.ensure(values.length == 1, root, name, "expected a tag, got %d values", values.length);
///         return builder.equal(path, values[0]);
///     }
///     ...
/// }
/// ```
///
/// It is whitelisted on an entity as `@Filterable(name = "withTag", filter = HasTag.class)`.
///
/// @param <T> the type of the property the path leads to
public interface TraversalFilter<T> extends Filter {

    /// @return the resolved path, whose group and quantifier decide whether and how the condition
    /// is folded into a subquery
    Traversal traversal();

    /// @param root the root the path starts from: the query root, or the correlated root of the
    /// subquery when the path crosses a collection
    /// @param path the property the traversal leads to, already joined
    /// @param builder the criteria builder
    /// @param values the values requested by the client, possibly containing `null`s
    /// @return the condition on the property
    /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when the
    /// values are not acceptable
    Predicate condition(Root<?> root, Path<T> path, CriteriaBuilder builder, String[] values);

    /// Applies the condition to the path resolved from the query root, with no subquery: the
    /// repository does not call it, and folds the conditions of collection paths itself.
    ///
    /// @param root the root of the query
    /// @param query the query
    /// @param builder the criteria builder
    /// @param values the values requested by the client
    /// @return the condition on the property
    @Override
    default Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
        final Path<T> path = Filters.path(root, name(), traversal());
        return condition(root, path, builder, values);
    }
}