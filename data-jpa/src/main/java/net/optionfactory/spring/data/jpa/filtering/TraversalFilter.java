package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

public interface TraversalFilter<T> extends Filter {

    Traversal traversal();

    Predicate condition(Root<?> root, Path<T> path, CriteriaBuilder builder, String[] values);

    @Override
    default Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
        final Path<T> path = Filters.path(root, name(), traversal());
        return condition(root, path, builder, values);
    }
}