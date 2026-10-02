package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;

/// A whitelisted, named filter: translates the string values of a [FilterRequest] entry into a
/// query predicate on the root entity.
///
/// Filters are instantiated once per repository, when it is created, from the filter annotations
/// on the entity (see [net.optionfactory.spring.data.jpa.filtering.filters.spi.WhitelistedFilter]).
/// A constructor should therefore check its preconditions on the [EntityType] — a single entity type, the
/// presence and type of the properties it reads — throwing an [InvalidFilterConfiguration], directly
/// or through the [Filters] utilities, so that a misconfigured filter fails at startup.
///
/// [#toPredicate] is then called once per query using the filter, with the values the client sent.
/// They are untrusted: an implementation must check them, throwing an [InvalidFilterRequest],
/// directly or through [Filters#ensure], so that a malformed request is reported as the client's
/// mistake rather than as a server error. An implementation must be thread-safe, as one instance
/// serves every concurrent query of its repository.
///
/// A filter reaching through a collection should rather implement [TraversalFilter], whose
/// conditions are folded into `EXISTS` subqueries for it.
public interface Filter {

    /// @return the name the filter is whitelisted under, which is the key a [FilterRequest] uses
    /// to request it
    String name();

    /// @param root the root of the query
    /// @param query the query, to build subqueries from
    /// @param builder the criteria builder
    /// @param values the values requested by the client, possibly containing `null`s
    /// @return the predicate restricting the query
    /// @throws InvalidFilterRequest when the values are not acceptable
    Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values);
}
