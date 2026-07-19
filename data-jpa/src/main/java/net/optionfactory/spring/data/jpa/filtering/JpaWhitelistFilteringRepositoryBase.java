package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository.SessionPolicy;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import org.hibernate.jpa.AvailableHints;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class JpaWhitelistFilteringRepositoryBase<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private final Map<String, Filter> allowedFilters;
    private final Map<String, String> allowedSorters;
    private final EntityManager entityManager;

    public JpaWhitelistFilteringRepositoryBase(JpaEntityInformation<T, ?> ei, EntityManager em) {
        super(ei, em);
        this.entityManager = em;
        this.allowedFilters = Repositories.allowedFilters(ei, em);
        this.allowedSorters = Repositories.allowedSorters(ei, em);
    }

    private static <T> Specification<T> where(@Nullable Specification<T> spec) {
        return spec == null ? Specification.unrestricted() : spec;
    }

    @Override
    public Page<T> findAll(@Nullable Specification<T> spec, Pageable pageable) {
        return super.findAll(where(spec), pageable);
    }

    @Override
    public List<T> findAll(@Nullable Specification<T> spec, Sort sort) {
        return super.findAll(where(spec), sort);
    }

    public Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters) {
        return findOne(where(base).and(filter(filters)));
    }

    public Page<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Pageable pageable) {
        return super.findAll(where(base).and(filter(filters)), pageable);
    }

    public List<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort) {
        return super.findAll(where(base).and(filter(filters)), sort);
    }

    public <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, BiFunction<SessionPolicy, T, R> callback) {
        final AtomicLong counter = new AtomicLong(-1);
        final SessionPolicy policy = new SessionPolicy(entityManager, counter);        
        return getQuery(where(base).and(filter(filters)), getDomainClass(), sort)
                .setHint(AvailableHints.HINT_FETCH_SIZE, fetchSize)
                .getResultStream()
                .peek(entity -> counter.incrementAndGet())
                .map(entity -> callback.apply(policy, entity));
    }

    public long count(@Nullable Specification<T> base, FilterRequest filters) {
        return count(where(base).and(filter(filters)));
    }

    public Optional<T> findOne(FilterRequest filters) {
        return findOne(null, filters);
    }

    public Page<T> findAll(FilterRequest filters, Pageable pageable) {
        return findAll(null, filters, pageable);
    }

    public List<T> findAll(@Nullable Specification<T> base, FilterRequest filters) {
        return findAll(base, filters, Sort.unsorted());
    }

    public List<T> findAll(FilterRequest filters) {
        return findAll(null, filters, Sort.unsorted());
    }

    public List<T> findAll(FilterRequest filters, Sort sort) {
        return findAll(null, filters, sort);
    }

    public <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, BiFunction<SessionPolicy, T, R> cb) {
        return findAll(null, filters, sort, fetchSize, cb);
    }

    public long count(FilterRequest filters) {
        return count(null, filters);
    }

    /*
     * 4. Centralized Interceptor Engine
     * This runs exclusively when building data retrieval queries.
     * We dynamically inject the sorting specification here and pass Sort.unsorted() 
     * down to the super method to prevent Spring Data from clobbering the chain.
     */
    @Override
    protected <S extends T> TypedQuery<S> getQuery(@Nullable Specification<S> spec, Class<S> domainClass, Sort sort) {
        Specification<S> sortingSpec = new WhitelistSortingSpecificationAdapter<>(sort, allowedSorters);
        Specification<S> combinedQuerySpec = spec == null ? sortingSpec : spec.and(sortingSpec);
        
        return super.getQuery(combinedQuerySpec, domainClass, Sort.unsorted());
    }

    private WhitelistFilteringSpecificationAdapter<T> filter(FilterRequest filters) {
        return new WhitelistFilteringSpecificationAdapter<>(filters, allowedFilters);
    }
}