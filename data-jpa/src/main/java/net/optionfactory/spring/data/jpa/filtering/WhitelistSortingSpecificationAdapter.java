package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Map;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Sorters;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/// A [Specification] that restricts nothing and appends the orders of a [Sort], resolved against
/// the sorters whitelisted on an entity, to the orders the query already has.
///
/// The repository combines it after the query's own specification, so the orders that
/// specification sets come first. A sort property is a sorter name: one that is not whitelisted is
/// rejected with an [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest]
/// when the specification is applied.
///
/// @param <T> the entity type
public class WhitelistSortingSpecificationAdapter<T> implements Specification<T> {

    private final Sort requested;
    private final Map<String, Traversal> allowed;

    /// @param requested the requested order, by sorter name
    /// @param allowed the sorters whitelisted on the entity, by name
    public WhitelistSortingSpecificationAdapter(Sort requested, Map<String, Traversal> allowed) {
        this.requested = requested;
        this.allowed = allowed;
    }

    /// Sets the query orders as a side effect.
    ///
    /// @param root the query root
    /// @param query the query whose orders are extended
    /// @param criteriaBuilder the criteria builder
    /// @return always `null`, as the specification restricts nothing
    /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest when a
    /// requested sorter is not whitelisted
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        query.orderBy(Stream.concat(
                query.getOrderList().stream(),
                Sorters.orders(root, criteriaBuilder, requested, allowed).stream()
        ).toArray(i -> new Order[i]));
        return null;
    }

}
