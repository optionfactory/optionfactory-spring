package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;

/**
 * Base class for filters that are referenced by the {@link Filterable}
 * annotation.
 *
 * A filter implementation should enforce preconditions on the
 * {@link EntityType}s it can be applied on (such as a single entity type, or
 * entities with a specific set of properties), throwing an
 * {@link InvalidFilterConfiguration} exception on precondition violation or by
 * using utility methods defined in {@link Filters}.
 *
 * The
 * {@link Filter#toPredicate(javax.persistence.criteria.Root, javax.persistence.criteria.CriteriaQuery, javax.persistence.criteria.CriteriaBuilder, java.lang.String[]) Filter.toPredicate}
 * method should also check preconditions on the given arguments, throwing an
 * {@link InvalidFilterRequest} exception on precondition violation or by using
 * utility methods defined in {@link Filters}.
 */
public abstract class CustomFilter implements Filter {

    private final String name;

    public CustomFilter(Filterable annotation) {
        name = annotation.name();
    }

    @Override
    public String name() {
        return name;
    }
}
