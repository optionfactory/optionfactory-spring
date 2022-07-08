package net.optionfactory.spring.data.jpa.filtering;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.filters.QueryMode;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;

public interface TraversalFilter<T> extends Filter {

    QueryMode mode();

    Traversal traversal();

    Predicate condition(Root<?> root, Path<T> path, CriteriaBuilder builder, String[] values);

    @Override
    default Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
        final var mq = Filters.prepare(root, query, builder, mode());
        final var path = Filters.<T>path(name(), mq.conditionRoot, traversal());
        final var condition = condition(root, path, builder, values);
        return Filters.apply(mq, condition);
    }

}
