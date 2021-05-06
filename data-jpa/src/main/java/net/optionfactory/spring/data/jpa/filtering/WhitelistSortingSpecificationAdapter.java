package net.optionfactory.spring.data.jpa.filtering;

import java.util.Map;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Sorters;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

public class WhitelistSortingSpecificationAdapter<T> implements Specification<T> {

    private final Sort requested;
    private final Map<String, String> allowed;

    public WhitelistSortingSpecificationAdapter(Sort requested, Map<String, String> allowed) {
        this.requested = requested;
        this.allowed = allowed;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        query.orderBy(Stream.concat(
                query.getOrderList().stream(),
                QueryUtils.toOrders(Sorters.validateAndTransform(root.getJavaType(), requested, allowed), root, criteriaBuilder).stream()
        ).toArray(i -> new Order[i]));
        return null;
    }

}
