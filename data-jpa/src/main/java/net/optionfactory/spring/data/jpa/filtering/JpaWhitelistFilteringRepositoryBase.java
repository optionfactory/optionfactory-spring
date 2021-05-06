package net.optionfactory.spring.data.jpa.filtering;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository.StreamingMode;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository.StreamingOptions;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Sorters;
import org.hibernate.jpa.QueryHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;

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

    @Override
    public Page<T> findAll(@Nullable Specification<T> spec, Pageable pageable) {
        return super.findAll(Specification.where(spec).and(sort(pageable.getSort())), unsorted(pageable));
    }

    @Override
    public List<T> findAll(@Nullable Specification<T> spec, Sort sort) {
        return findAll(Specification.where(spec).and(sort(sort)));
    }

    public Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters) {
        return findOne(Specification.where(base).and(filter(filters)));
    }

    public Page<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Pageable pageable) {
        return findAll(Specification.where(base).and(filter(filters)).and(sort(pageable.getSort())), unsorted(pageable));
    }

    public List<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort) {
        return findAll(Specification.where(base).and(filter(filters)).and(sort(sort)), Sort.unsorted());
    }

    public Stream<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, StreamingOptions so) {
        final var stream = getQuery(Specification.where(base).and(filter(filters)).and(sort(sort)), Sort.unsorted())
                .setHint(QueryHints.HINT_FETCH_SIZE, so.fetchSize)
                .getResultStream();
        return so.mode == StreamingMode.DETACHED ? stream.peek(entityManager::detach) : stream;
    }

    public <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, StreamingOptions so, Function<T, R> beforeDetaching) {
        return getQuery(Specification.where(base).and(filter(filters)).and(sort(sort)), Sort.unsorted())
                .setHint(QueryHints.HINT_FETCH_SIZE, so.fetchSize)
                .getResultStream()
                .map(entity -> {
                    final var r = beforeDetaching.apply(entity);
                    if (so.mode == StreamingMode.DETACHED) {
                        entityManager.detach(entity);
                    }
                    return r;
                });
    }

    public long count(@Nullable Specification<T> base, FilterRequest filters) {
        return count(Specification.where(base).and(filter(filters)));
    }

    @Override
    protected <S extends T> TypedQuery<S> getQuery(@Nullable Specification<S> spec, Class<S> domainClass, Sort sort) {
        return super.getQuery(spec, domainClass, Sorters.validateAndTransform(domainClass, sort, allowedSorters));
    }

    private WhitelistFilteringSpecificationAdapter<T> filter(FilterRequest filters) {
        return new WhitelistFilteringSpecificationAdapter<>(filters, allowedFilters);
    }

    private WhitelistSortingSpecificationAdapter<T> sort(Sort req) {
        return new WhitelistSortingSpecificationAdapter<>(req, allowedSorters);
    }

    private static Pageable unsorted(Pageable page) {
        return page.isPaged() ? PageRequest.of(page.getPageNumber(), page.getPageSize()) : Pageable.unpaged();
    }

}
