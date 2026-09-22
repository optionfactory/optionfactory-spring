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

public class WhitelistSortingSpecificationAdapter<T> implements Specification<T> {

    private final Sort requested;
    private final Map<String, Traversal> allowed;

    public WhitelistSortingSpecificationAdapter(Sort requested, Map<String, Traversal> allowed) {
        this.requested = requested;
        this.allowed = allowed;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        query.orderBy(Stream.concat(
                query.getOrderList().stream(),
                Sorters.orders(root, criteriaBuilder, requested, allowed).stream()
        ).toArray(i -> new Order[i]));
        return null;
    }

}
