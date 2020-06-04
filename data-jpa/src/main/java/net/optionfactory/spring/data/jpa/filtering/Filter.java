package net.optionfactory.spring.data.jpa.filtering;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;

/**
 * Parametric filter.
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
public interface Filter {

    /**
     * The filter name, which is referenced by {@link FilterRequest}s.
     */
    String name();

    /**
     * Translates filter arguments to a query predicate.
     *
     * @param root
     * @param query
     * @param builder
     * @param values filter arguments
     */
    Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values);
}
