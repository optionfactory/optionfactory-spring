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
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.springframework.data.jpa.domain.Specification;

public class WhitelistFilteringSpecificationAdapter<T> implements Specification<T> {

    private final Map<String, String[]> requested;
    private final Map<String, Filter> whitelisted;

    public WhitelistFilteringSpecificationAdapter(FilterRequest requested, Map<String, Filter> whitelisted) {
        this.requested = requested.filters() == null ? Map.of() : requested.filters();
        this.whitelisted = whitelisted;
    }

    /**
     * Requested filters and subquery groups are both visited in name order: the emitted
     * predicates then depend only on which filters were requested, never on the iteration
     * order of the map carrying them, so the same logical request always renders the same
     * SQL text and databases can reuse its cached plan.
     */
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
                // enqueue subselects for folding
                subselectGroups.computeIfAbsent(tf.traversal().group(), k -> new ArrayList<>()).add(e);
            } else if (spec instanceof TraversalFilter tf) {
                // route standard path-based queries directly inline
                final Path path = Filters.path(root, name, tf.traversal());
                predicates.add(tf.condition(root, path, builder, e.getValue()));
            } else {
                // route custom filters
                predicates.add(spec.toPredicate(root, query, builder, e.getValue()));
            }
        }

        // fold all subqueries into EXISTS expressions
        for (List<Map.Entry<String, String[]>> group : subselectGroups.values()) {
            final Subquery<Integer> sq = query.subquery(Integer.class);
            final Root<?> conditionRoot = sq.from(root.getJavaType());

            final List<Predicate> groupPredicates = new ArrayList<>();
            groupPredicates.add(builder.equal(conditionRoot, root));

            for (Map.Entry<String, String[]> e : group) {
                final TraversalFilter tf = (TraversalFilter) whitelisted.get(e.getKey());
                final Path path = Filters.path(conditionRoot, e.getKey(), tf.traversal());
                groupPredicates.add(tf.condition(conditionRoot, path, builder, e.getValue()));
            }

            sq.select(builder.literal(1)).where(groupPredicates.toArray(Predicate[]::new));
            predicates.add(builder.exists(sq));
        }

        if (predicates.isEmpty()) {
            // an unrestricted Specification: lets callers drop the where clause entirely,
            // rather than emitting a `1=1` no-one asked for
            return null;
        }
        return builder.and(predicates.toArray(Predicate[]::new));
    }
}