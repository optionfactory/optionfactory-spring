package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.optionfactory.spring.data.jpa.filtering.filters.Match;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.springframework.data.jpa.domain.Specification;

/// The [Specification] applying a [FilterRequest] against the filters whitelisted on an entity, as
/// the repository does for every filtered query.
///
/// Filters are combined in `AND`. The conditions of [TraversalFilter]s whose path crosses a
/// collection are grouped by their traversal group: each group becomes one correlated `EXISTS`
/// subquery holding the conditions of all its filters, so that they describe the same element,
/// negated when the group's quantifier is
/// [net.optionfactory.spring.data.jpa.filtering.filters.Match#NONE]. The other traversal filters
/// are applied inline, on joins from the query root, and the remaining filters through their own
/// [Filter#toPredicate].
///
/// @param <T> the entity type
public class WhitelistFilteringSpecificationAdapter<T> implements Specification<T> {

    private final Map<String, String[]> requested;
    private final Map<String, Filter> whitelisted;

    /// @param requested the filters requested by the client; a `null` map is read as no filters
    /// @param whitelisted the filters whitelisted on the entity, by name
    public WhitelistFilteringSpecificationAdapter(FilterRequest requested, Map<String, Filter> whitelisted) {
        this.requested = requested.filters() == null ? Map.of() : requested.filters();
        this.whitelisted = whitelisted;
    }

    /// Requested filters and subquery groups are both visited in name order: the emitted
    /// predicates then depend only on which filters were requested, never on the iteration order of
    /// the map carrying them, so the same logical request always renders the same SQL text and
    /// databases can reuse its cached plan.
    ///
    /// A subquery correlates the parent rather than selecting it again: the collection is joined
    /// straight from it, so the parent table is read once instead of once per `EXISTS`. Every
    /// traversal of a group opens the same collection, hence carries the same quantifier, read from
    /// the first filter of the group.
    ///
    /// @param root the query root
    /// @param query the query, subqueries are created from
    /// @param builder the criteria builder
    /// @return the conjunction of the requested filters, or `null` when none is requested: an
    /// unrestricted specification, letting the query drop the `where` clause rather than emit a
    /// `1=1`
    /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest when a
    /// requested filter is not whitelisted, or rejects its values
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        final List<Predicate> predicates = new ArrayList<>();
        final Map<String, List<Map.Entry<String, String[]>>> subselectGroups = new TreeMap<>();

        for (Map.Entry<String, String[]> e : requested.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            final String name = e.getKey();
            final Filter spec = whitelisted.get(name);

            if (spec == null) {
                throw new InvalidFilterRequest(name, root, "filter not configured in root object");
            }

            if (spec instanceof TraversalFilter tf && tf.traversal().group() != null) {
                subselectGroups.computeIfAbsent(tf.traversal().group(), k -> new ArrayList<>()).add(e);
            } else if (spec instanceof TraversalFilter tf) {
                final Path path = Filters.path(root, name, tf.traversal());
                predicates.add(tf.condition(root, path, builder, e.getValue()));
            } else {
                predicates.add(spec.toPredicate(root, query, builder, e.getValue()));
            }
        }

        for (List<Map.Entry<String, String[]>> group : subselectGroups.values()) {
            final Subquery<Integer> sq = query.subquery(Integer.class);
            final Root<T> conditionRoot = sq.correlate(root);

            final List<Predicate> groupPredicates = new ArrayList<>();
            for (Map.Entry<String, String[]> e : group) {
                final TraversalFilter tf = (TraversalFilter) whitelisted.get(e.getKey());
                final Path path = Filters.path(conditionRoot, e.getKey(), tf.traversal());
                groupPredicates.add(tf.condition(conditionRoot, path, builder, e.getValue()));
            }

            sq.select(builder.literal(1)).where(groupPredicates.toArray(Predicate[]::new));
            final var match = ((TraversalFilter<?>) whitelisted.get(group.get(0).getKey())).traversal().match();
            final var exists = builder.exists(sq);
            predicates.add(match == Match.NONE ? builder.not(exists) : exists);
        }

        if (predicates.isEmpty()) {
            return null;
        }
        return builder.and(predicates.toArray(Predicate[]::new));
    }
}