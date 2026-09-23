package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.filters.Match;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import org.springframework.data.domain.Sort;

/// Utility methods for evaluating whitelisted sorter traversals and validating sort runtime requests.
///
/// Sorters resolve their paths through the same [Filters#traversal] engine as filters, so a path
/// crossing an association is navigated with the very same joins (and honours the same
/// [net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal] overrides), letting a
/// filter and a sorter on the same path share one join.
public class Sorters {

    /// Resolves a whitelisted sorter path against the entity metamodel.
    ///
    /// Unlike a filter, a sorter cannot fold a collection into an `EXISTS` subquery: an `ORDER BY`
    /// has to name an expression of the selected row, so a plural hop could only be rendered as a
    /// join from the root, multiplying rows. The row multiplication then silently corrupts
    /// pagination, as the `LIMIT` counts joined rows while the count query counts entities: pages
    /// come back short, some entities repeat across pages and others never appear at all. Such
    /// paths are therefore rejected here, when the repository is built, rather than producing
    /// wrong pages at request time.
    ///
    /// @param entity the JPA root metamodel descriptor
    /// @param sorterName the identifier of the sorter being evaluated
    /// @param path the raw dot-separated target path
    /// @return the resolved graph traversal
    public static Traversal traversal(EntityType<?> entity, String sorterName, String path) {
        final Traversal traversal;
        try {
            traversal = Filters.traversal(entity, sorterName, path, Match.ANY);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSortConfiguration(sorterName, entity, String.format("cannot resolve path %s: %s", path, ex.getMessage()));
        }
        if (traversal.group() != null) {
            throw new InvalidSortConfiguration(sorterName, entity, String.format("path %s crosses a collection: sorting on a plural attribute multiplies rows, breaking pagination", path));
        }
        return traversal;
    }

    public static List<Order> orders(Root<?> root, CriteriaBuilder builder, Sort requested, Map<String, Traversal> allowed) {
        final var orders = new ArrayList<Order>();
        for (Sort.Order order : requested) {
            orders.add(order(root, builder, order, allowed));
        }
        return orders;
    }

    @SuppressWarnings("unchecked")
    public static Order order(Root<?> root, CriteriaBuilder builder, Sort.Order requested, Map<String, Traversal> allowed) {
        final String name = requested.getProperty();
        final Traversal traversal = allowed.get(name);
        if (traversal == null) {
            throw new InvalidSortRequest(name, root.getJavaType(), "sorter not configured in root object");
        }
        final Path<?> path = Filters.path(root, name, traversal);
        final Expression<?> expression = requested.isIgnoreCase() && String.class.equals(path.getJavaType())
                ? builder.lower((Expression<String>) path)
                : path;
        final Nulls nulls = nulls(requested.getNullHandling());
        return requested.isAscending() ? builder.asc(expression, nulls) : builder.desc(expression, nulls);
    }

    private static Nulls nulls(Sort.NullHandling nullHandling) {
        return switch (nullHandling) {
            case NATIVE ->
                Nulls.NONE;
            case NULLS_FIRST ->
                Nulls.FIRST;
            case NULLS_LAST ->
                Nulls.LAST;
        };
    }
}
