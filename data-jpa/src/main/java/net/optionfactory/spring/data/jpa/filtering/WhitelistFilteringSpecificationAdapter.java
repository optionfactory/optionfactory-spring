package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.springframework.data.jpa.domain.Specification;

public class WhitelistFilteringSpecificationAdapter<T> implements Specification<T> {

    private final Map<String, String[]> requested;
    private final Map<String, Filter> whitelisted;

    public WhitelistFilteringSpecificationAdapter(FilterRequest requested, Map<String, Filter> whitelisted) {
        this.requested = requested.filters == null ? Map.of() : requested.filters;
        this.whitelisted = whitelisted;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        final Predicate[] predicates = requested.entrySet().stream().map(e -> {
            final String name = e.getKey();
            final String[] values = e.getValue();
            final Filter spec = whitelisted.get(name);
            if (spec == null) {
                throw new InvalidFilterRequest(name, root, "filter not configured in root object");
            }
            return spec.toPredicate(root, query, builder, values);
        }).toArray(size -> new Predicate[size]);
        return builder.and(predicates);
    }

}
