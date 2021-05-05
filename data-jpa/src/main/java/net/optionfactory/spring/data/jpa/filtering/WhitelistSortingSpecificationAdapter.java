package net.optionfactory.spring.data.jpa.filtering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

public class WhitelistSortingSpecificationAdapter<T> implements Specification<T> {

    private final Sort requested;
    private final Map<String, String> whitelisted;

    public WhitelistSortingSpecificationAdapter(Sort requested, Map<String, String> whitelisted) {
        this.requested = requested != null ? requested : Sort.unsorted();
        this.whitelisted = whitelisted;
        
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        final List<Order> orders = new ArrayList<>(query.getOrderList());
        orders.addAll(QueryUtils.toOrders(requested, root, criteriaBuilder));
        query.orderBy(orders);
        return null;
    }

}
