package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository.SessionPolicy;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import org.hibernate.jpa.AvailableHints;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

/// The repository base class installed by [EnableJpaWhitelistFilteringRepositories], implementing
/// the [WhitelistFilteringRepository] methods for the repository interfaces that declare them.
///
/// It reads the filter and sorter whitelists of the entity once, when the repository is created, so
/// that a misconfigured annotation fails the startup. It also overrides how spring data builds its
/// select queries: the [Sort] of every query, including those of the inherited `findAll(Sort)` and
/// `findAll(Pageable)` methods, is resolved against the whitelisted sorters, so a sort property is
/// a sorter name and a property that is not one is rejected with an
/// [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest]. Count queries are
/// not sorted, and not affected.
///
/// It declares no transactional behaviour: repositories join the caller's transaction, and
/// [WhitelistFilteringRepositoryFactoryBean] rejects calls made outside one.
///
/// @param <T> the entity type
/// @param <ID> the entity identifier type
public class JpaWhitelistFilteringRepositoryBase<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private final Map<String, Filter> allowedFilters;
    private final Map<String, Traversal> allowedSorters;
    private final EntityManager entityManager;

    /// Called by spring data for each repository.
    ///
    /// @param ei the entity metadata
    /// @param em the entity manager of the repository
    /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration
    /// when a filter annotation on the entity is misconfigured
    /// @throws net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortConfiguration
    /// when a sorter annotation on the entity is misconfigured
    /// @throws IllegalStateException when two filters share a name, or a filter class has no
    /// suitable constructor
    public JpaWhitelistFilteringRepositoryBase(JpaEntityInformation<T, ?> ei, EntityManager em) {
        super(ei, em);
        this.entityManager = em;
        this.allowedFilters = Repositories.allowedFilters(ei, em);
        this.allowedSorters = Repositories.allowedSorters(ei, em);
    }

    private static <T> Specification<T> where(@Nullable Specification<T> spec) {
        return spec == null ? Specification.unrestricted() : spec;
    }

    /// Accepts a `null` specification as unrestricted.
    ///
    /// @param spec the restriction, or `null` for none
    /// @param pageable the requested page, sorted by whitelisted sorter names
    /// @return the requested page of the matching entities
    @Override
    public Page<T> findAll(@Nullable Specification<T> spec, Pageable pageable) {
        return super.findAll(where(spec), pageable);
    }

    /// Accepts a `null` specification as unrestricted.
    ///
    /// @param spec the restriction, or `null` for none
    /// @param sort the requested order, by whitelisted sorter names
    /// @return every matching entity, sorted
    public List<T> findAll(@Nullable Specification<T> spec, Sort sort) {
        return super.findAll(where(spec), sort);
    }

    /// See [WhitelistFilteringRepository#findOne(Specification, FilterRequest)].
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return the single matching entity, or empty when none does
    public Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters) {
        return findOne(where(base).and(filter(filters)));
    }

    /// See [WhitelistFilteringRepository#findAll(Specification, FilterRequest, Pageable)].
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param pageable the requested page, sorted by whitelisted sorter names
    /// @return the requested page of the matching entities
    public Page<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Pageable pageable) {
        return super.findAll(where(base).and(filter(filters)), pageable);
    }

    /// See [WhitelistFilteringRepository#findAll(Specification, FilterRequest, Sort)].
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @return every matching entity, sorted
    public List<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort) {
        return super.findAll(where(base).and(filter(filters)), sort);
    }

    /// See [WhitelistFilteringRepository#findAll(Specification, FilterRequest, Sort, int, Function)].
    ///
    /// @param <R> the result type
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mapper the transformation applied to each entity before it is detached
    /// @return the mapped entities, sorted
    public <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper) {
        return findAll(base, filters, sort, fetchSize, SessionPolicy.Mode.READ_ONLY, (policy, entity) -> {
            final R mapped = mapper.apply(entity);
            policy.detaching(entity);
            return mapped;
        });
    }

    /// See [WhitelistFilteringRepository#findAll(FilterRequest, Sort, int, Function)].
    ///
    /// @param <R> the result type
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mapper the transformation applied to each entity before it is detached
    /// @return the mapped entities, sorted
    public <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper) {
        return findAll(null, filters, sort, fetchSize, mapper);
    }

    /// See [WhitelistFilteringRepository#findAll(Specification, FilterRequest, Sort, int, SessionPolicy.Mode, BiFunction)].
    ///
    /// The row counter is incremented in the mapping step itself, right before the callback, rather
    /// than in a separate `peek`: the callback is then guaranteed to see the 1-based number of the
    /// row it is deciding upon when it calls [SessionPolicy#clearIf].
    ///
    /// @param <R> the result type
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mode how entities are loaded
    /// @param beforeDetaching the transformation applied to each entity
    /// @return the mapped entities, sorted
    public <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching) {
        final AtomicLong counter = new AtomicLong(0);
        final SessionPolicy policy = new SessionPolicy(entityManager, counter);
        final var query = getQuery(where(base).and(filter(filters)), getDomainClass(), sort)
                .setHint(AvailableHints.HINT_FETCH_SIZE, fetchSize);
        if (mode == SessionPolicy.Mode.READ_ONLY) {
            query.setHint(AvailableHints.HINT_READ_ONLY, true);
        }
        return query.getResultStream()
                .map(entity -> {
                    counter.incrementAndGet();
                    return beforeDetaching.apply(policy, entity);
                });
    }

    /// See [WhitelistFilteringRepository#findAll(FilterRequest, Sort, int, SessionPolicy.Mode, BiFunction)].
    ///
    /// @param <R> the result type
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mode how entities are loaded
    /// @param beforeDetaching the transformation applied to each entity
    /// @return the mapped entities, sorted
    public <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching) {
        return findAll(null, filters, sort, fetchSize, mode, beforeDetaching);
    }

    /// See [WhitelistFilteringRepository#count(Specification, FilterRequest)].
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return the number of matching entities
    public long count(@Nullable Specification<T> base, FilterRequest filters) {
        return count(where(base).and(filter(filters)));
    }

    /// See [WhitelistFilteringRepository#findOne(FilterRequest)].
    ///
    /// @param filters the requested filters
    /// @return the single matching entity, or empty when none does
    public Optional<T> findOne(FilterRequest filters) {
        return findOne(null, filters);
    }

    /// See [WhitelistFilteringRepository#findAll(FilterRequest, Pageable)].
    ///
    /// @param filters the requested filters
    /// @param pageable the requested page, sorted by whitelisted sorter names
    /// @return the requested page of the matching entities
    public Page<T> findAll(FilterRequest filters, Pageable pageable) {
        return findAll(null, filters, pageable);
    }

    /// See [WhitelistFilteringRepository#findAll(Specification, FilterRequest)].
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return every matching entity, in no particular order
    public List<T> findAll(@Nullable Specification<T> base, FilterRequest filters) {
        return findAll(base, filters, Sort.unsorted());
    }

    /// See [WhitelistFilteringRepository#findAll(FilterRequest)].
    ///
    /// @param filters the requested filters
    /// @return every matching entity, in no particular order
    public List<T> findAll(FilterRequest filters) {
        return findAll(null, filters, Sort.unsorted());
    }

    /// See [WhitelistFilteringRepository#findAll(FilterRequest, Sort)].
    ///
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @return every matching entity, sorted
    public List<T> findAll(FilterRequest filters, Sort sort) {
        return findAll(null, filters, sort);
    }

    /// See [WhitelistFilteringRepository#count(FilterRequest)].
    ///
    /// @param filters the requested filters
    /// @return the number of matching entities
    public long count(FilterRequest filters) {
        return count(null, filters);
    }

    /// Builds every select query, and only those: count queries are built elsewhere. The requested
    /// sort is turned into a specification resolving whitelisted sorters, combined after `spec`,
    /// and `Sort.unsorted()` is passed down so that spring data does not apply the sort a second
    /// time, as entity properties, replacing the orders set by the specifications.
    @Override
    protected <S extends T> TypedQuery<S> getQuery(@Nullable Specification<S> spec, Class<S> domainClass, Sort sort) {
        final var sortingSpec = new WhitelistSortingSpecificationAdapter<S>(sort, allowedSorters);
        final var combinedQuerySpec = spec == null ? sortingSpec : spec.and(sortingSpec);
        return super.getQuery(combinedQuerySpec, domainClass, Sort.unsorted());
    }

    private WhitelistFilteringSpecificationAdapter<T> filter(FilterRequest filters) {
        return new WhitelistFilteringSpecificationAdapter<>(filters, allowedFilters);
    }
}
